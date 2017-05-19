package core
import java.util.concurrent.Executors

import io.netty.util.concurrent.DefaultThreadFactory

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

object ExecutorContexts {
  val imageService = Executors.newFixedThreadPool(Conf.imageExecutorNum, new DefaultThreadFactory("image"))
  implicit val imageContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(imageService)

  val commonHandlerService = Executors.newFixedThreadPool(Conf.commonHandlerExecutorNum, new DefaultThreadFactory("common-handler"))
  implicit val commonHandlerContext: ExecutionContextExecutorService = ExecutionContext.fromExecutorService(commonHandlerService)
}
