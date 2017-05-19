package core.util
import io.netty.handler.codec.http.QueryStringDecoder

/**
 * Легковесная обёртка над QueryStringDecoder
 */
class UrlEncodedFromDecoder(decoder: QueryStringDecoder) {

  import scala.collection.JavaConverters._

  def get(key: String): Option[String] = decoder.parameters().get(key) match {
    case null => None
    case e if e.size() > 0 => Some(e.get(0))
    case _ => None
  }

  def get(key: String, default: String): String = decoder.parameters().get(key) match {
    case null => default
    case e if e.size() > 0 => e.get(0)
    case _ => default
  }

  def getAll(key: String): Iterable[String] = decoder.parameters().get(key).asScala

  def contains(key: String): Boolean = decoder.parameters().containsKey(key)

  lazy val toMap: Map[String, String] = {
    val b = Map.newBuilder[String, String]
    for (entry <- decoder.parameters().entrySet().asScala) b += ((entry.getKey, entry.getValue.get(0)))
    b.result()
  }
}
