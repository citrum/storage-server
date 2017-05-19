package server.http

import java.net.SocketAddress

import io.netty.handler.codec.http.{QueryStringDecoder, HttpRequest}
import org.apache.commons.lang3.StringUtils
import io.netty.handler.codec.http.multipart.FileUpload
import core.Token
import core.util.UrlEncodedFromDecoder

class Request(val req: HttpRequest,
              val remoteAddress: SocketAddress,
              val fileUpload: Option[FileUpload] = None,
              val token: Option[Token] = None,
              val rawBody: Array[Byte] = Array.emptyByteArray) {
  def method = req.getMethod
  val uri = req.getUri
  val path = StringUtils.substringBefore(uri, "?")
  def headers = req.headers()
  lazy val params = new UrlEncodedFromDecoder(new QueryStringDecoder(uri))
}
