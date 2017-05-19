package views
import core._
import org.slf4j.LoggerFactory
import server.http.{Request, Result}

object TokenHandler {
  private val log = LoggerFactory.getLogger(getClass)

  def create(req: Request): Result = {
    ServerRequest(req) {
      val token: Token = try {
        Js.mapper.readValue(req.rawBody, classOf[Token])
      } catch {
        case e: Exception =>
          if (Profile.isLocalOrJenkins) log.error("Error parsing token for request:\n" + new String(req.rawBody), e)
          return Result.BadRequest("Error parsing token")
      }
      token.setId(TokenManager.generateId)
      log.info("Token created: " + token.id)
      TokenManager.add(token)
      Result.Ok(token.id)
    }
  }
}
