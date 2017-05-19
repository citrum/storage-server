package views
import core.{Js, Profile, ServerRequest, ThumbnailWhiteList}
import org.slf4j.LoggerFactory
import server.http.{Request, Result}

object ThumbnailWhiteListHandler {
  private val log = LoggerFactory.getLogger(getClass)

  case class WhiteListReq(set: Set[String])

  def update(req: Request): Result = {
    ServerRequest(req) {
      val whiteListReq: WhiteListReq = try {
        Js.mapper.readValue(req.rawBody, classOf[WhiteListReq])
      } catch {
        case e: Exception =>
          if (Profile.isLocalOrJenkins) log.error("Error parsing whitelist for request:\n" + new String(req.rawBody), e)
          return Result.BadRequest("Error parsing whitelist")
      }
      ThumbnailWhiteList.setNewList(whiteListReq.set)
      Result.Ok(whiteListReq.set.size.toString)
    }
  }
}
