package server

import java.io.IOException

import core._
import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel._
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.multipart.InterfaceHttpData.HttpDataType
import io.netty.handler.codec.http.multipart._
import io.netty.util.CharsetUtil
import org.apache.commons.lang3.StringUtils
import org.slf4j.{LoggerFactory, MarkerFactory}
import views.Route

import scala.util.{Failure, Success}

class NettyHandler(handlerId: Int, https: Boolean) extends SimpleChannelInboundHandler[HttpObject] {
  import NettyHandler._

  private val logMarker = MarkerFactory.getDetachedMarker("h" + handlerId)
  log.debug(logMarker, "Started")

  private var request: HttpRequest = null
  private var decoder: HttpPostRequestDecoder = null
  private var fileUpload: Option[FileUpload] = None
  private var token: Token = null
  private var allowRawBody: Boolean = false
  @volatile private var noResetOnDisconnect: Boolean = false

  override def channelUnregistered(ctx: ChannelHandlerContext): Unit = {
    if (!noResetOnDisconnect) reset()
    log.debug(logMarker, "channelUnregistered, decoder:{}", decoder)
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject) {
    msg match {
      case request: HttpRequest =>
        this.request = request
        Thread.currentThread().setName("netty@" + Thread.currentThread().getId + ": " + request.getMethod.name() + " " + request.getUri)
        log.debug(logMarker, "{}: {}", request.getMethod: Any, request.getUri: Any)

        request.getMethod match {
          case HttpMethod.OPTIONS =>
            val response: FullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, OK)
            response.headers().add(ACCESS_CONTROL_ALLOW_ORIGIN, Site.accessAllowOrigin(request))
            response.headers().add(ACCESS_CONTROL_ALLOW_METHODS, "OPTIONS, GET, POST")
            response.headers().add(ACCESS_CONTROL_ALLOW_HEADERS, "content-type")
            response.headers().add(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true")
            response.headers().set(CONTENT_LENGTH, 0)
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)

          case HttpMethod.GET =>
            handleRequest(ctx, new http.Request(request, ctx.channel().remoteAddress()))

          case HttpMethod.POST =>
            request.headers().get(CONTENT_TYPE) match {
              case null =>
                sendError(ctx, request, HttpResponseStatus.BAD_REQUEST, "No content-type")

              case d if d.startsWith("multipart/form-data") =>
                // Проверка токена
                val tokenId = StringUtils.substringAfter(request.getUri, "?")
                if (tokenId.isEmpty) sendError(ctx, request, HttpResponseStatus.NOT_FOUND, "Empty token")
                else {
                  token = TokenManager.get(tokenId).getOrElse {
                    sendError(ctx, request, HttpResponseStatus.NOT_FOUND, "Token not found")
                    return
                  }
                  token.fileSize =
                    try request.headers().get(CONTENT_LENGTH).toInt
                    catch {
                      case _: NumberFormatException =>
                        sendError(ctx, request, HttpResponseStatus.BAD_REQUEST, "Invalid content-length")
                        return
                    }
                  if (token.fileSize > token.jpgMaxUploadSize) {
                    sendErrorJson(ctx, request, ErrorMessages.tooBig(token.maxStoredSize))
                    return
                  }

                  token.readByteCount = 0
                  try {
                    if (decoder != null) decoder.destroy()
                    decoder = new HttpPostRequestDecoder(factory, request)
                  } catch {
                    case e1: HttpPostRequestDecoder.ErrorDataDecoderException =>
                      sendError(ctx, request, HttpResponseStatus.BAD_REQUEST, "Error decoding post data")
                  }
                  token.uploadStarted = true
                }

              case d if d.startsWith("application/json") =>
                // Пришёл json - примем его
                if (request.headers().get(CONTENT_LENGTH).toInt > Conf.maxJsonPostSize)
                  sendError(ctx, request, HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, "Request entity too large")
                else
                  allowRawBody = true

              case _ =>
                // Мы не поддерживаем другие POST-запросы.
                sendError(ctx, request, HttpResponseStatus.NOT_IMPLEMENTED, "Not implemented")
            }

          case _ =>
            sendError(ctx, request, HttpResponseStatus.NOT_IMPLEMENTED, "Not implemented")
        }

      // check if the decoder was constructed before
      // if not it handles the form get
      case chunk: HttpContent =>
        if (decoder != null && token != null) {
          // Получить часть в режиме аплоада
          TokenManager.touch(token.id) // Обновить last access time
          token.readByteCount += chunk.content().capacity()

          try {
            decoder.offer(chunk)
          } catch {
            case _: HttpPostRequestDecoder.ErrorDataDecoderException |
                 _: StringIndexOutOfBoundsException |
                 _: ArrayIndexOutOfBoundsException =>
              sendError(ctx, request, HttpResponseStatus.BAD_REQUEST, "Error decoding post data")
              log.info(logMarker, "Error decoding post data, request:" + request.getUri)
              decoder.destroy() // Освобождаем внутренние ресурсы декодера
              decoder = null
              return
          }
          // reading chunk by chunk (minimize memory usage due to Factory)
          readHttpDataChunkByChunk()
          if (chunk.isInstanceOf[LastHttpContent]) {
            token.uploadFinished = true
            handleRequest(ctx,
              new http.Request(request, ctx.channel().remoteAddress(), fileUpload = fileUpload, token = Some(token)))
          }
        }
        else if (allowRawBody) {
          if (chunk.isInstanceOf[LastHttpContent]) {
            val content: ByteBuf = chunk.content()
            val readableBytes: Int = content.readableBytes()
            val bytes: Array[Byte] =
              if (readableBytes > 0) {
                val bytes = new Array[Byte](readableBytes)
                content.readBytes(bytes)
                bytes
              } else Array.emptyByteArray
            handleRequest(ctx, new http.Request(request, ctx.channel().remoteAddress(), rawBody = bytes))
          } else throw new UnsupportedOperationException("Cannot handle chunked posts")
        }

      case _ => ()
    }
  }

  private def reset() = {
    // Синхронизация здесь нужна потому что reset() вызывается из handleRequest(onComplete), и из
    // channelUnregistered, причём одновременно с разных потоков.
    synchronized {
      log.debug(logMarker, "reset, decoder:{}, fileUpload:{}", decoder: Any, fileUpload: Any)
      request = null
      if (decoder != null) {
        decoder.destroy()
        decoder = null
      }
      fileUpload.foreach(_.release())
      fileUpload = None
      token = null
      allowRawBody = false
    }
  }

  /**
   * Example of reading request by chunk and getting values from chunk to chunk
   */
  private def readHttpDataChunkByChunk() {
    try {
      while (decoder.hasNext) {
        val data: InterfaceHttpData = decoder.next
        if (data != null) {
          var releaseData: Boolean = true
          try {
            if (data.getHttpDataType eq HttpDataType.FileUpload) {
              val dataFileUpload: FileUpload = data.asInstanceOf[FileUpload]
              if (dataFileUpload.isCompleted && this.fileUpload.isEmpty && dataFileUpload.length() > 0) {
                log.debug(logMarker, "readHttpDataChunkByChunk set fileUpload: {}", dataFileUpload)
                this.fileUpload = Some(dataFileUpload)
                releaseData = false
              }
            }
          } finally {
            if (releaseData) {
              log.debug(logMarker, "readHttpDataChunkByChunk releaseData")
              data.release
            }
          }
        }
      }
    } catch {
      case e1: HttpPostRequestDecoder.EndOfDataDecoderException =>
      //        responseContent.append("\r\n\r\nEND OF CONTENT CHUNK BY CHUNK\r\n\r\n")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    cause match {
      // Игнорируем сообщения типа Connection reset by peer, Connection timed out
      case e: IOException => ()
      case e => log.warn(logMarker, "Netty: " + e.getClass.getSimpleName + ": " + e.getMessage, e)
    }
    if (ctx.channel().isActive) {
      log.debug(logMarker, "exceptionCaught channel close")
      ctx.channel().close()
    }
  }

  private def sendError(ctx: ChannelHandlerContext, req: HttpRequest, status: HttpResponseStatus, message: String) {
    log.debug(logMarker, "sendError {}: {}", status.code(), message)
    val content: ByteBuf = Unpooled.copiedBuffer(message + "\r\n", CharsetUtil.UTF_8)
    val resp: FullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, content)
    resp.headers().set(CONTENT_TYPE, "text/plain; charset=UTF-8")
    resp.headers().add(ACCESS_CONTROL_ALLOW_ORIGIN, Site.accessAllowOrigin(req))
    resp.headers().set(CONTENT_LENGTH, content.readableBytes())
    resp.content().writeBytes(content)

    // Close the connection as soon as the error message is sent.
    ctx.writeAndFlush(resp).addListener(new ChannelFutureListener {
      override def operationComplete(future: ChannelFuture): Unit = {
        log.debug(logMarker, "sendError channel close")
        future.channel().close()
        // Закомментарил, потому что выдаёт IllegalReferenceCountException: refCnt: 0, decrement: 1 // content.release()
      }
    })
  }

  private def sendOk(ctx: ChannelHandlerContext, req: HttpRequest, content: String) {
    log.debug(logMarker, "sendOk: {}", content)
    val resp: FullHttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    resp.headers().set(CONTENT_TYPE, "application/json; charset=UTF-8")
    resp.headers().add(ACCESS_CONTROL_ALLOW_ORIGIN, Site.accessAllowOrigin(req))
    val bytes: Array[Byte] = content.getBytes
    resp.headers().set(CONTENT_LENGTH, bytes.length)
    resp.content().writeBytes(bytes)

    // Close the connection as soon as the error message is sent.
    ctx.writeAndFlush(resp)
    // Здесь не стоит сразу разрывать соединение, иначе XMLHttpRequest в браузере не сможет прочитать тело сообщения.
    //      .addListener(ChannelFutureListener.CLOSE)
  }

  private def sendErrorJson(ctx: ChannelHandlerContext, req: HttpRequest, message: String) {
    sendOk(ctx, req, "{\"error\":\"" + message + "\"}")
  }

  private def handleRequest(ctx: ChannelHandlerContext, httpReq: http.Request) {
    import ExecutorContexts.commonHandlerContext
    noResetOnDisconnect = true
    Route.handle(httpReq).onComplete {tryResult =>
      try {
        tryResult match {
          case Success(result) =>
            val resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, result.status)
            // Set response headers
            for ((name, value) <- result.headers)
              resp.headers().add(name, value)

            resp.headers().set(CONTENT_LENGTH, result.body.length)
            resp.headers().add(ACCESS_CONTROL_ALLOW_ORIGIN, Site.accessAllowOrigin(httpReq.req))

            resp.content().writeBytes(result.body)
            val writeFuture: ChannelFuture = ctx.writeAndFlush(resp)
            writeFuture.addListener(ChannelFutureListener.CLOSE)

            PageLog.write(PageLog(httpReq, result.status.code().toString))
            log.debug(logMarker, "Request handled: {}", result.status.code())

          case Failure(e) =>
            PageLog.write(PageLog(httpReq, e.toString))
            log.error(logMarker, e.toString, e) // e.toString в тексте сообщения для Sentry
            sendError(ctx, httpReq.req, INTERNAL_SERVER_ERROR, "Internal server error")
        }
      } finally {
        Thread.currentThread().setName("netty@" + Thread.currentThread().getId + ": [empty]")
        reset()
      }
    }
  }
}


object NettyHandler {
  private final val factory: HttpDataFactory = new DefaultHttpDataFactory(DefaultHttpDataFactory.MINSIZE)
  private final val log = LoggerFactory.getLogger(classOf[NettyHandler])
}
