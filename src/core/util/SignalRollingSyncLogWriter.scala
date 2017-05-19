package core.util
import java.io.{File, FileOutputStream, IOException, OutputStream}
import java.nio.charset.Charset

import com.google.common.base.Charsets
import org.slf4j.LoggerFactory
import utils.{SigHandler, Signals}

/**
 * LogWriter, который умеет переоткрывать свой лог-файл при получении сигнала USR2.
 * В лог пишет синхронизированно.
 *
 * @param file Файл лога
 * @param bufferSize Размер буфера перед записью лога
 */
class SignalRollingSyncLogWriter(file: File, bufferSize: Int = 8192, charset: Charset = Charsets.UTF_8) {
  private val lock = new Object
  private var fileStream: FileOutputStream = null
  private var stream: OutputStream = null

  openWriter()

  Signals.install(Signals.USR2, new SigHandler {
    def handle(signal: String) {
      lock.synchronized {
        try {
          closeWriter()
          openWriter()
        } catch {
          case e: IOException =>
            // Не уверен, что код обработки ошибки правильный. Возможно, его стоит переделать.
            LoggerFactory.getLogger(getClass).error("LogWriter(" + file.getAbsolutePath + ") RolloverFailure occurred. Deferring roll-over.")
        }
      }
    }
  })

  def closeWriter() {
    lock.synchronized {
      if (stream != null) {
        stream.flush()
        stream.close()
        fileStream.close()
      }
      stream = null
      fileStream = null

    }
  }

  def openWriter() {
    lock.synchronized {
      if (stream != null) sys.error("Already opened")
      fileStream = new FileOutputStream(file, true)
      stream = if (bufferSize == 0) fileStream else new FastBufferedOutputStream(fileStream, bufferSize)
    }
  }

  def write(bytes: Array[Byte]): Unit = lock synchronized stream.write(bytes)
  def write(str: String): Unit = lock synchronized stream.write(str.getBytes(charset))
  def writeLn(str: String): Unit = lock synchronized {
    stream.write(str.getBytes(charset))
    stream.write('\n')
  }
}
