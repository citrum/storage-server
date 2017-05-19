package core
import java.net.URL

import io.netty.handler.codec.http.{HttpHeaders, HttpRequest}

import scala.util.Try

object Site {
  /**
    * Важно! При переходе на https нужно не забыть поправить здесь урл на https://
    */
  def accessAllowOrigin(req: HttpRequest): String = {
    if (Profile.isProd) {
      val origin: String = req.headers().get(HttpHeaders.Names.ORIGIN)
      val url: URL = Try(new URL(origin)).getOrElse(return "https://" + Conf.originDomains(0))
      if (Conf.originDomains.exists(d => url.getHost == d || isSubDomain(d, url.getHost))) origin
      else url.getProtocol + "://" + Conf.originDomains(0)
    } else {
      req.headers().get(HttpHeaders.Names.ORIGIN) match {
        case null => req.headers().get(HttpHeaders.Names.REFERER) match {
          case null => "*"
          case referer =>
            val url: URL = new URL(referer)
            url.getProtocol + "://" + url.getHost
        }
        case origin => origin
      }
    }
  }

  /** Проверяет, является ли #sub поддоменом от #domain */
  def isSubDomain(domain: String, sub: String): Boolean = {
    sub.endsWith(domain) &&
      domain.length + 2 <= sub.length &&
      sub.charAt(sub.length - domain.length - 1) == '.'
  }
}
