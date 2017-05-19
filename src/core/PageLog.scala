package core
import java.io.File
import java.net.InetSocketAddress
import java.text.SimpleDateFormat
import java.util.Date

import core.util.SignalRollingSyncLogWriter
import server.http

object PageLog {
  val logWriter = new SignalRollingSyncLogWriter(new File(
    if (Profile.isProd) "/var/log/storage-server/page.log" else "/tmp/storage-server/page.log"
  ))

  val dateFormat = new ThreadLocal[SimpleDateFormat] {
    override def initialValue() = {
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS")
    }
  }

  def write(log: PageLog) {
    logWriter.writeLn(log.toString)
  }
}

case class PageLog(req: http.Request, result: String, now: Date = new Date()) {
  override def toString: String = {
    val sb = new StringBuilder(64)
    sb append PageLog.dateFormat.get().format(now)
    req.remoteAddress match {
      case inetAddress: InetSocketAddress => sb append " ip:" append inetAddress.getAddress.getHostAddress
      case _ => ()
    }
    sb append " \"" append req.method.name() append " " append req.uri append "\""
    sb append " ret:" append result
    sb.toString()
  }
}
