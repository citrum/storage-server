package core
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.{Files, Paths, StandardCopyOption}
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

import com.fasterxml.jackson.annotation.{JsonAutoDetect, JsonIgnore, JsonProperty}
import com.google.common.cache.{Cache, CacheBuilder}
import com.google.common.collect.Sets
import com.sun.image.codec.jpeg.JPEGCodec
import core.util.{FileExt, NameGen}
import io.netty.handler.codec.http.multipart.FileUpload
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.concurrent.Future

/**
  * Токен загружаемого файла. Создаётся специальной командой от сервера. Токен содержит в себе все параметры этого файла и его лимиты.
  * Примерный расход памяти на 32-битной машине при загрузке jpeg 18.5mb 23680px × 2144px ~ 400mb
  */
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
  setterVisibility = JsonAutoDetect.Visibility.NONE, creatorVisibility = JsonAutoDetect.Visibility.NONE,
  fieldVisibility = JsonAutoDetect.Visibility.NONE)
class Token {
  /** Уникальный id токена */
  private var _id: String = null

  /** true - если допустимо загружать только jpg,
    * false - можно загружать любой файл, поддерживаемый классом [[FileExt]].
    */
  @JsonProperty val strictlyJpg: Boolean = false

  /**
    * Если указан, то этот список ограничивает возможные загружаемые расширения файла.
    * В случае, если требуется загрузить только jpg, то нужно установить флаг [[strictlyJpg]], а этот список не указывать.
    */
  @JsonProperty val allowedExtensions: Option[Array[String]] = None

  /** Максимальный размер картинки.
    * При превышении этого размера, картинка будет уменьшена с сохранением пропорций так, чтобы
    * вписаться в этот размер.
    */
  @JsonProperty val maxSize: Option[ImageSize] = None

  /** Политика уменьшения картинки до размера [[maxSize]]. */
  @JsonProperty("resizePolicy") val resizePolicyStr: String = ResizePolicy.Fit.toString
  def resizePolicy: ResizePolicy.Value = ResizePolicy.withName(resizePolicyStr)

  /** Цвет RGB, которым будут заполнены поля при политике уменьшения [[ResizePolicy.Grow]].
    * Например, красный цвет задаётся так: 0xff0000.
    */
  @JsonProperty val resizeGrowColor: Int = 0 // чёрный

  /** Максимальный размер файла, который будет храниться на сервере.
    * Если загружается не jpg, то сервер не даст загрузить файл больше этого значения.
    * Если загружается jpg большего размера (но не более [[jpgMaxUploadSize]]), то сервер уменьшит её размер примерно до [[maxStoredSize]].
    */
  @JsonProperty val maxStoredSize: Int = 2 * 1000000

  /** При загрузке jpg это максимальный размер файла, который сервер может принять. */
  @JsonProperty val jpgMaxUploadSize: Int = 20 * 1000000

  import ExecutorContexts.imageContext

  var uploadStarted: Boolean = false
  var uploadFinished: Boolean = false
  var fileSize: Int = 0
  var readByteCount: Int = 0
  var imageFuture: Option[Future[Unit]] = None
  var error: Option[String] = None
  var path: String = null
  @volatile var completed: Boolean = false

  def onUpload(upload: FileUpload, pathWithoutExt: String, name: String, ext: String) {
    val isJpg: Boolean = FileExt.isJpg(ext)
    if (strictlyJpg && !isJpg) {error = Some(ErrorMessages.needJpg); return}
    if (!isJpg && upload.length() > maxStoredSize) {error = Some(ErrorMessages.tooBig(maxStoredSize)); return}

    Files.createDirectories(Paths.get(pathWithoutExt).getParent)
    val targetPath = Paths.get(pathWithoutExt + "." + ext)
    path = Dirs.tmp.externalPath + name + "." + ext

    if (FileExt.isImage(ext)) {
      // Process image in another thread
      Files.createDirectories(Conf.tmpDir)
      // Здесь мы сохраняем загруженную картинку в tempFile, чтобы она не удалилась при вызове метода upload.release()
      val tempPath = Files.createTempFile(Conf.tmpDir, "upload-", "." + ext)
      try {
        require(upload.renameTo(tempPath.toFile), s"Error renaming $upload to $tempPath")
      } catch {
        case e: IOException =>
          throw new IOException(e.getMessage +
            " (upload length:" + upload.length + ", filename:" + upload.getFilename + ")", e)
      }
      var tempFileRenamed = false
      imageFuture = Some(Future {
        val source: BufferedImage =
          try ImageIO.read(tempPath.toFile)
          catch {
            case e: Exception =>
              error = Some(ErrorMessages.invalidImage)
              Files.delete(tempPath)
              null
          }
        if (source != null) {
          try {
            var updated: BufferedImage = source
            val resized: Boolean =
              if (maxSize.exists(sz => source.getWidth > sz.w || source.getHeight > sz.h)) {
                updated = Resizer.resize(source, maxSize.get, resizePolicy, resizeGrowColor)
                true
              } else false

            if (isJpg) {
              val out = Files.newOutputStream(targetPath)
              val encoder = JPEGCodec.createJPEGEncoder(out)
              val param = JPEGCodec.getDefaultJPEGEncodeParam(updated)
              param.setQuality(0.85f, true)
              encoder.encode(updated, param)
              out.close()
            } else {
              ImageIO.write(updated, ext, targetPath.toFile)
            }

            // Проверить, не получился ли у нас вес картинки больше чем в исходнике?
            if (!resized && Files.size(targetPath) > Files.size(tempPath)) {
              // Если мы не ресайзили картинку, а новый файл получился больше, то просто отбросим его, и возьмём файл юзера.
              Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING)
              Files.setPosixFilePermissions(targetPath, {
                import java.nio.file.attribute.PosixFilePermission._
                Sets.newHashSet(OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ)
              })
              tempFileRenamed = true
            }

            // Создать thumbnails
            //            if (thumbnails.nonEmpty) {
            //              // Если у картинки есть альфа-канал, сделать её копию без альфа-канала в sourceRgb
            //              val sourceRgb: BufferedImage = if (source.getColorModel.hasAlpha) {
            //                val img: BufferedImage = new BufferedImage(source.getWidth, source.getHeight, BufferedImage.TYPE_INT_RGB)
            //                val g = img.createGraphics()
            //                try g.drawImage(source, 0, 0, source.getWidth, source.getHeight, Color.WHITE, null)
            //                catch {case e: Exception => g.dispose(); throw e}
            //                img
            //              } else source
            //
            //              for (tn <- thumbnails) {
            //                val tnBuilder: Builder[BufferedImage] = Thumbnails.of(sourceRgb).size(tn.w, tn.h)
            //                if (resizePolicy == ResizePolicy.Crop) tnBuilder.crop(Positions.CENTER)
            //                tnBuilder.toFile(tn.file(pathWithoutExt, "jpg"))
            //              }
            //            }
            completed = true
          } catch {
            case e@(_: Exception | _: OutOfMemoryError) =>
              // В случае ошибки удалить все созданные файлы
              error = Some(ErrorMessages.imageProcessingError)
              Files.delete(targetPath)
              //              thumbnails.foreach(tn => tn.file(pathWithoutExt, "jpg").delete())
              LoggerFactory.getLogger("image").warn(s"Error processing image, tokenId:$id, path:$pathWithoutExt", e)

          } finally {
            require(completed || error.isDefined, s"Invalid state after processing image path:$pathWithoutExt, tempFile:$tempPath")
            if (!tempFileRenamed) Files.delete(tempPath)
          }
        }
      })
    } else {
      // Process binary file
      require(upload.renameTo(targetPath.toFile), s"Error renaming $upload to $targetPath")
      completed = true
    }
  }

  def id: String = _id
  def setId(newId: String) {
    if (_id != null) sys.error("Id already set")
    _id = newId
  }
}

case class ImageSize(w: Int, h: Int) {
  @JsonIgnore def isValid: Boolean = w > 0 && h > 0
}


object Token {
  val log = LoggerFactory.getLogger(getClass)
}


object TokenManager {
  val idLength = 6

  val cache: Cache[String, Token] = CacheBuilder
    .newBuilder()
    .maximumSize(100000)
    .expireAfterAccess(5, TimeUnit.MINUTES)
    .build[String, Token]()

  def add(token: Token) {
    cache.put(token.id, token)
  }

  def get(id: String): Option[Token] = Option(cache.getIfPresent(id))
  def touch(id: String): Token = cache.getIfPresent(id)

  @tailrec
  def generateId: String = {
    val id = NameGen.generateName(idLength)
    if (cache.getIfPresent(id) == null) id else generateId
  }
}
