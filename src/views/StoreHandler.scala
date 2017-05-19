package views
import java.nio.file.{Files, Path, Paths}

import core.Dirs.Naming
import core.util.{IOUtils, LockSet}
import core.{Dirs, ServerRequest}
import org.apache.commons.lang3.StringUtils
import server.http.{Request, Result}

import scala.collection.JavaConversions._

object StoreHandler {

  case class StoreResult(path: String, fileSize: Int)

  private val storeLockSet: LockSet[String] = LockSet.lazyWeakLock[String](64)

  /**
    * Переместить временный файл в постоянное хранилище.
    */
  def store(req: Request): Result = {
    ServerRequest(req) {
      val suffix: String = getAndCheckSuffix(req, "suffix").fold(return _, a => a)
      val file: Path = getAndCheckPath(req, "tmpname", Dirs.tmp).fold(return _, a => a)
      val fileName: String = file.getFileName.toString

      storeLockSet.withLock(fileName) {
        val dotIdx = StringUtils.lastIndexOf(fileName, '.')
        if (dotIdx == -1) return Result.BadRequest("Invalid tmpname")
        val nameWithTilde = fileName.substring(0, dotIdx) + '~'
        val ext = fileName.substring(dotIdx + 1)

        var alreadyStored = false
        val newName: String =
          if (Files.exists(file)) {
            // Сгенерировать новое имя
            Dirs.store.getNewNameWithoutExt("." + suffix, ext)
          } else {
            // Если файл не найден - он может быть уже сохранён в постоянном хранилище. Проверить это.
            val stored: Path = toStoredRedirect(file)
            if (!Files.exists(stored)) return Result.NotFound
            alreadyStored = true
            IOUtils.readString(stored)
          }

        val newPath: Path = Dirs.store.toPath(newName + "." + ext)

        if (!alreadyStored) {
          Files.createDirectories(newPath.getParent)
          // Найти основной и дополнительные файлы (thumbnails) и переместить их все
          val tmpFileDir: Path = file.getParent
          for (subPathStream <- resource.managed(Files.newDirectoryStream(tmpFileDir));
               subPath <- subPathStream) {
            val subName = subPath.getFileName.toString
            if (subName.startsWith(nameWithTilde)) {
              val ending = subName.substring(nameWithTilde.length - 1)
              val tmpSubPath = tmpFileDir.resolve(subName)
              val newSubPath = Dirs.store.toPath(newName + ending)
              Files.move(tmpSubPath, newSubPath)
            }
          }
          Files.move(file, newPath)
          require(Files.exists(newPath), s"Error storing file $newPath (file not found)")

          // Сохранить имя нового файла для последующих запросов.
          val stored: Path = toStoredRedirect(file)
          IOUtils.writeString(stored, newName)
        } else {
          if (!Files.exists(newPath)) return Result.NotFound(s"File seems to be stored to $newPath and deleted")
        }

        Result.OkJson(StoreResult(Dirs.store.externalPath + newName + "." + ext, fileSize = Files.size(newPath).toInt))
      }
    }
  }

  case class DeleteResult(removed: Int)

  private val deleteLockSet: LockSet[String] = LockSet.lazyWeakLock[String](64)

  /**
    * Удалить файл из постоянного хранилища.
    */
  def delete(req: Request): Result = {
    ServerRequest(req) {
      val path: Path = getAndCheckPath(req, "name", Dirs.store).fold(return _, a => a)
      val fileName: String = path.getFileName.toString

      deleteLockSet.withLock(fileName) {
        if (!Files.exists(path)) return Result.NotFound

        val dotIdx = StringUtils.lastIndexOf(fileName, '.')
        if (dotIdx == -1) return Result.BadRequest("Invalid name")
        val nameWithTilde = fileName.substring(0, dotIdx) + '~'

        // Найти основной и дополнительные файлы (thumbnails) и удалить их
        var delCount = 0
        val dir: Path = path.getParent
        for (subPathStream <- resource.managed(Files.newDirectoryStream(dir));
             subPath <- subPathStream) {
          val subName = subPath.getFileName.toString
          if (subName.startsWith(nameWithTilde)) {
            Files.delete(subPath)
            delCount += 1
          }
        }
        Files.delete(path)
        Result.OkJson(DeleteResult(delCount))
      }
    }
  }

  private def getAndCheckSuffix(req: Request, paramName: String): Either[Result, String] = {
    val suffix: String = req.params.get(paramName).getOrElse(return Left(Result.BadRequest("No suffix")))
    if (suffix.isEmpty) return Left(Result.BadRequest("Empty suffix"))
    if (!StringUtils.containsOnly(suffix, "abcdefghijklmnopqrstuvwxyz0123456789-_")) return Left(Result.BadRequest("Suffix contains invalid chars"))
    Right(suffix)
  }

  private def getAndCheckPath(req: Request, paramName: String, naming: Naming): Either[Result, Path] = {
    val name: String = req.params.get(paramName).getOrElse(return Left(Result.BadRequest("No " + paramName)))
    if (!name.startsWith(naming.externalPath)) return Left(Result.BadRequest("Invalid baseDir for " + paramName))
    val fullName = name.substring(naming.externalPath.length)
    if (fullName.length < naming.nameLength || StringUtils.contains(fullName, '/')) return Left(Result.BadRequest("Bad " + paramName))
    val file = naming.toPath(fullName)
    Right(file)
  }

  private def toStoredRedirect(path: Path): Path = Paths.get(path.toAbsolutePath.toString + ".stored")
}
