package server.http
import io.netty.handler.codec.http.HttpResponseStatus
import scala.collection.mutable
import org.intellij.lang.annotations.Language
import core.Js

class Result(val status: HttpResponseStatus, val body: Array[Byte]) {
  val headers: mutable.Buffer[(String, String)] = mutable.Buffer[(String, String)]()

  /**
   * Adds HTTP headers to this result.
   */
  def withHeader(name: String, value: String): this.type = {
    headers += ((name, value))
    this
  }
}

object Result {
  def Ok(@Language("HTML") html: String): Result = new Result(HttpResponseStatus.OK, html.getBytes)
  def Ok(bytes: Array[Byte]): Result = new Result(HttpResponseStatus.OK, bytes)
  def Ok: Result = Ok(Array.emptyByteArray)
  def OkJson(obj: Object): Result = Ok(Js.mapper.writeValueAsBytes(obj))

  def NotFound: Result = new Result(HttpResponseStatus.NOT_FOUND, Array.emptyByteArray)
  def NotFound(message: String): Result = new Result(HttpResponseStatus.NOT_FOUND, message.getBytes)

  def BadRequest: Result = new Result(HttpResponseStatus.BAD_REQUEST, Array.emptyByteArray)
  def BadRequest(message: String): Result = new Result(HttpResponseStatus.BAD_REQUEST, message.getBytes)
}
