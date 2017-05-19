package views
import core.util.FileExt
import core.{Dirs, ErrorMessages, TokenManager}
import io.netty.handler.codec.http.HttpHeaders.Names._
import org.apache.commons.lang3.StringUtils
import server.http.{Request, Result}

object UploadHandler {

  case class InfoResult(read: Int, total: Int, path: String, completed: Boolean)
  case class InfoError(error: String)

  /**
    * Получить информацию о загружаемом файле.
    */
  def info(req: Request): Result = {
    val tokenId = StringUtils.substringAfter(req.uri, "?")
    if (tokenId.isEmpty) return Result.BadRequest("Empty token")
    val token = TokenManager.get(tokenId).getOrElse(return Result.NotFound("Token not found"))
    (token.error match {
      case Some(msg) => Result.OkJson(InfoError(msg))
      case None => Result.OkJson(InfoResult(read = token.readByteCount, total = token.fileSize, path = token.path, completed = token.completed))
    }).withHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
  }


  case class UploadResult(ok: Boolean = true, filename: String, path: String)
  case class UploadError(error: String)

  /**
    * Загрузить файл во временное хранилище.
    * При запросе на загрузку нужно обязательно указать токен. Пример: /upload?123abc, где 123abc - токен.
    */
  def uploadTemp(req: Request): Result = {
    val token = req.token.getOrElse(return Result.BadRequest("Empty token"))
    val upload = req.fileUpload.getOrElse {
      token.error = Some(ErrorMessages.emptyFile)
      return Result.BadRequest("Empty fileUpload")
    }
    require(upload.length() > 0L, "FileUpload length is " + upload.length())

    val ext = FileExt.detectAndValidate(upload, token).getOrElse {
      token.error = Some(ErrorMessages.invalidExt)
      return Result.OkJson(UploadError(ErrorMessages.invalidExt))
    }
    val name: String = Dirs.tmp.getNewNameWithoutExt("", ext)
    val pathWithoutExt: String = Dirs.tmp.baseDir.toAbsolutePath.toString + "/" + Dirs.tmp.splitToPath(name)
    token.onUpload(upload, pathWithoutExt, name, ext)
    Result.OkJson(UploadResult(filename = upload.getFilename, path = token.path))
    // Важно! В этот момент на клиент уходит ответ - типа всё хорошо. Но это не значит, что загрузка
    // файла завершена. Это всего лишь сообщение о том, что файл принят и он получил имя.
    // Клиенты должны проверять состояние загрузки, опрашивая метод '/info'.
  }
}
