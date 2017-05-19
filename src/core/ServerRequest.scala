package core
import server.http.{Request, Result}
import io.netty.handler.codec.http.HttpResponseStatus

object ServerRequest {
  /**
   * Обёртка над обработчиком запроса, авторизующая только запросы от сервера.
   */
  def apply(req: Request)(body: => Result): Result = {
    if (Profile.isLocal) body
    else {
      val secret = req.headers.get("Secret")
      if (secret == null || secret != Conf.serverSecret) new Result(HttpResponseStatus.FORBIDDEN, "Invalid secret".getBytes)
      else body
    }
  }
}
