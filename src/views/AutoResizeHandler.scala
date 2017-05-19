package views
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.{Files, Path, Paths}
import javax.imageio.ImageIO

import com.google.common.net.HttpHeaders
import core._
import core.util.{FileExt, LockSet}
import org.apache.commons.lang3.StringUtils
import server.http.{Request, Result}

import scala.concurrent.Future
import scala.util.control.ControlThrowable

/**
  * Обработчик автоматического ресайза картинок, создаваемых динамически под параметры пришедшего запроса.
  * Полученная картинка сохраняется в файл и отдаётся клиенту. Следующие запросы этой картинки уже
  * будут отдаваться nginx'ом.
  */
object AutoResizeHandler {
  import ExecutorContexts.imageContext

  private val lockSet: LockSet[String] = LockSet.lazyWeakLock[String](32)

  def resize(req: Request): Future[Result] =
    resizeForUrl(StringUtils.substringAfter(req.uri, "?"))

  def resizeForUrl(gotUrl: String): Future[Result] = {
    gotUrl match {
      case url if url.startsWith("/file/tmp/") => processResizeRequest(url.substring(10), Dirs.tmp)
      case url if url.startsWith("/file/") => processResizeRequest(url.substring(6), Dirs.store)
      case _ => notFoundFuture
    }
  }

  private def notFound = Result.NotFound("Not found")
  private def notFoundFuture = Future(notFound)

  private def processResizeRequest(gotFileName: String, dirsNaming: Dirs.Naming): Future[Result] = {
    var fileName = gotFileName
    if (fileName.indexOf('/') != -1) {
      // В качестве запроса может прийти "k5a3vps2p0.file.odt/My_report.odt",
      // в таком случае имя файла записано до первого слеша.
      fileName = StringUtils.substringBefore(fileName, "/")
    }
    fileName.indexOf('~') match {
      case -1 => notFoundFuture
      case tildeIdx =>
        fileName.lastIndexOf('.') match {
          case -1 => notFoundFuture
          case idx if idx < tildeIdx => notFoundFuture
          case extIdx =>
            if (!ThumbnailWhiteList.isAllowedName(fileName)) Future(Result.BadRequest("Not allowed"))
            else {
              val gotParams: String = fileName.substring(tildeIdx + 1, extIdx)
              val ext: String = fileName.substring(extIdx + 1)
              val origPath: Path = dirsNaming.toPath(fileName.substring(0, tildeIdx) + '.' + ext)
              val savePath: Path = dirsNaming.toPath(fileName)

              if (!FileExt.isImage(ext)) notFoundFuture
              else {
                Future[Result](lockSet.withLock(fileName) {
                  if (Files.exists(savePath)) {
                    // Возможно, сюда пришло параллельно несколько одинаковых запросов, поэтому
                    // нужно предусмотреть простую выдачу уже существующего файла.
                    val bytes: Array[Byte] = Files.readAllBytes(savePath)
                    Result.Ok(bytes).withHeader(HttpHeaders.CONTENT_TYPE, FileExt.getMimeType(ext))

                  } else if (Files.exists(origPath)) {
                    if (!Files.isRegularFile(origPath)) notFound
                    else doResize(origPath, savePath, gotParams, ext)

                  } else {
                    // Оригинальный файл не найден, возможно стоит проверить файлы с другим расширением
                    if (ext != "jpg") notFound
                    else {
                      // Если запрашивается jpg-thumbnail, то попытаться найти картинки с другим расширением.
                      // Почему jpg? С ним превьюшки получаются, как правило, меньшего размера, чем с другими форматами.
                      val pathNoExtWithDot = StringUtils.removeEnd(origPath.toString, "jpg")
                      def tryExtensions(): Result = {
                        Seq("png", "gif").foreach {tryExt =>
                          val path: Path = Paths.get(pathNoExtWithDot + tryExt)
                          if (Files.exists(path) && Files.isRegularFile(path))
                            return doResize(path, savePath, gotParams, ext)
                        }
                        notFound
                      }
                      tryExtensions()
                    }
                  }
                })
              }
            }
        }
    }
  }

  private def doResize(origPath: Path, savePath: Path, gotParams: String, ext: String): Result = {
    case class ResultException(r: Result) extends ControlThrowable
    try {
      val params: Params = new Params()
      params.parse(gotParams).left.foreach(msg => return Result.BadRequest(msg))

      val sourceImage: BufferedImage = ImageIO.read(origPath.toFile)
      var doSymlink = false
      if (params.size.w >= sourceImage.getWidth && params.size.h >= sourceImage.getHeight) {
        // Thumbnail оказался больше, либо равен по размерам, чем сам оригинал
        if (params.policy == ResizePolicy.Fit) doSymlink = true
        else {
          // Если пропорции строго соблюдены, то просто делаем симлинк. Браузер сам растянет как надо.
          // Если же пропорции не соблюдены, то нужно делать ресайз, иначе пропорции поплывут.
          val calcHeight: Long = sourceImage.getHeight.toLong * params.size.w / sourceImage.getWidth
          doSymlink = params.size.h == calcHeight
        }
      }

      val bytes: Array[Byte] =
        if (doSymlink) {
          Files.deleteIfExists(savePath)
          Files.createSymbolicLink(savePath, savePath.getParent.relativize(origPath))
          Files.readAllBytes(origPath)
        } else {
          val resultImage: BufferedImage = Resizer.resize(sourceImage,
            params.size,
            policy = params.policy,
            resizeGrowColor = params.resizeGrowColor)

          val bos: ByteArrayOutputStream = new ByteArrayOutputStream(4096)
          ImageIO.write(resultImage, ext, bos)
          val bytes: Array[Byte] = bos.toByteArray
          Files.write(savePath, bytes)
          bytes
        }
      Result.Ok(bytes).withHeader(HttpHeaders.CONTENT_TYPE, FileExt.getMimeType(ext))
    } catch {
      case ResultException(r) => r
    }
  }


  /**
    * Разборщик пришедших параметров.
    * Он работает в довольно жёстких правилах:
    * * Размер обязателен и идёт первым параметром
    * * Свойства нельзя определять по два раза
    * * Порядок передачи параметр должен быть строго соблюдён
    *
    * Все эти ограничения сделаны для того, чтобы не создавать множество одинаковых файлов
    * с одинаковыми параметрами, но заданными по-разному. Должно получиться так, чтобы
    * был только один файл для заданного набора параметров. Соответственно, должна быть
    * только одна форма записи этих параметров.
    */
  class Params {
    var size: ImageSize = _
    var policy: ResizePolicy.Value = ResizePolicy.Crop
    var resizeGrowColor: Int = 0

    def parse(gotParams: String): Either[String, Unit] = {
      val params: Array[String] = StringUtils.splitPreserveAllTokens(gotParams, '~')
      if (params.length == 0) return Left("No params")

      var paramIdx = 1
      def parseParam(body: String => Boolean): Unit = {
        if (paramIdx < params.length) {
          if (body(params(paramIdx))) paramIdx += 1
        }
      }

      size = parseSizeParam(params(0)).getOrElse(return Left("Invalid size"))

      parseParam {
        case "fit" =>
          policy = ResizePolicy.Fit
          true
        case growR(hexColor) =>
          policy = ResizePolicy.Grow
          resizeGrowColor = Integer.parseUnsignedInt(hexColor, 16)
          true
        case _ => false
      }

      if (paramIdx < params.length) Left("Unknown params")
      else Right(())
    }

    private[views] def parseSizeParam(gotSize: String): Option[ImageSize] = {
      gotSize.indexOf('x') match {
        case -1 => None
        case idx => try {
          val w = gotSize.substring(0, idx).toInt
          val h = gotSize.substring(idx + 1).toInt
          val sz: ImageSize = ImageSize(w, h)
          if (sz.isValid) Some(sz)
          else None
        } catch {
          case e: Exception => None
        }
      }
    }
  }

  private val growR = """grow\.([0-9a-f]{6})""".r
}
