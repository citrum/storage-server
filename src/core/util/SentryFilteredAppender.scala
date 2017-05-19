package core.util

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.getsentry.raven.logback.SentryAppender
import server.NettyServer

/**
  * Расширение стандартного [[SentryAppender]].
  * Также, отсеивает все логи ниже WARN, и специфичные WARN-логи при старте/остановке сервера.
  */
class SentryFilteredAppender extends SentryAppender {
  /**
    * Отфильтровать сообщения, которые просто уведомляют о старте и остановке сервера.
    * Они являются ворнингами для того, чтобы попасть в app-warn.log, как разделитель между ошибками,
    * а также они имеют другой цвет при выводе в консоли ошибок.
    * Но они не являются ворнингами в привычном смысле.
    */
  override def append(iLoggingEvent: ILoggingEvent): Unit = {
    // Логи уровня INFO пропускаем
    if (!iLoggingEvent.getLevel.isGreaterOrEqual(Level.WARN)) return

    if (iLoggingEvent.getLevel == Level.WARN && iLoggingEvent.getLoggerName == classOf[NettyServer].getName) {
      iLoggingEvent.getMessage match {
        case "Server stopped" => return
        case m if m.startsWith("---------") => return
        case _ =>
      }
    }
    super.append(iLoggingEvent)
  }
}
