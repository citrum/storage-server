package views

import core.Profile
import server.http.{Request, Result}

import scala.concurrent.Future

object Route {
  import core.ExecutorContexts.commonHandlerContext

  val routes: Map[String, (Request) => Result] = {
    val mainRoutes = Map[String, Request => Result](
      "/create-token" -> TokenHandler.create _,
      "/info" -> UploadHandler.info _,
      "/upload-temp" -> UploadHandler.uploadTemp _,
      "/store" -> StoreHandler.store _,
      "/delete" -> StoreHandler.delete _,
      "/thumbnail-white-list" -> ThumbnailWhiteListHandler.update _
    )

    if (Profile.isLocalOrJenkins) mainRoutes ++ Seq(
      "/test" -> TestHandler.index _
    )
    else mainRoutes
  }

  val asyncRoutes: Map[String, (Request) => Future[Result]] = Map(
    "/auto-resize" -> AutoResizeHandler.resize _
  )

  def handle(req: Request): Future[Result] = {
    routes.get(req.path) match {
      case Some(fn) => Future(fn(req))
      case None =>
        if (Profile.isLocalOrJenkins && req.path.startsWith("/file/")) TestHandler.file(req)
        else
          asyncRoutes.get(req.path) match {
            case Some(fn) => fn(req)
            case None => Future(Result.NotFound)
          }
    }
  }
}
