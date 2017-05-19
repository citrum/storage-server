package views
import java.io.File
import java.nio.file.{Files, Path}

import core.Dirs.Naming
import core.{Dirs, ExecutorContexts, Profile}
import org.apache.commons.lang3.StringUtils
import server.http.{Request, Result}

import scala.concurrent.Future
import scala.io.Source

object TestHandler {
  import ExecutorContexts.imageContext

  def index(req: Request): Result = {
    if (!Profile.isLocalOrJenkins) return Result.NotFound

    Result.Ok(
      if (Profile.isLocal) Source.fromFile(new File("conf/test.html")).mkString
      else Source.fromURL(Thread.currentThread().getContextClassLoader.getResource("test.html")).mkString)
  }

  def file(req: Request): Future[Result] = {
    if (!Profile.isLocalOrJenkins) return Future(Result.NotFound)

    val path = StringUtils.removeStart(req.uri, "/file/")
    val naming: Naming = {
      if (path.startsWith(Dirs.tmp.externalPath)) Dirs.tmp
      else if (path.startsWith(Dirs.store.externalPath)) Dirs.store
      else return Future(Result.BadRequest)
    }
    val name = StringUtils.substringBefore(path.substring(naming.externalPath.length), "/")
    val file: Path = naming.toPath(name)
    if (Files.exists(file)) Future(Result.Ok(Files.readAllBytes(file)))
    else {
      AutoResizeHandler.resizeForUrl(req.uri)
    }
  }
}
