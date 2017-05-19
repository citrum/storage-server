package core
import java.io.File
import java.nio.file.Paths

import org.apache.commons.lang3.StringUtils

object Conf {
  val baseDir = Paths.get(System.getProperty("baseDir", "."))
  val tmpDir = Paths.get(System.getProperty("tmpDir", "/tmp/storage-server"))
  val workerThreadNum = System.getProperty("workerThreadNum", "4").toInt
  val httpAddress = System.getProperty("http.address", "0.0.0.0")
  val httpPort: Int = System.getProperty("http.port", "9010").toInt

  val httpsPort: Int = System.getProperty("https.port", "9014").toInt
  val sslKeyCertChainFile: Option[File] = Option(System.getProperty("ssl.crtFile")).map(new File(_))
  val sslPrivateKeyFile: Option[File] = Option(System.getProperty("ssl.keyFile")).map(new File(_))

  val imageExecutorNum = System.getProperty("imageExecutorNum", "6").toInt
  val commonHandlerExecutorNum = System.getProperty("commonHandlerExecutorNum", "4").toInt
  val serverSecret = System.getProperty("serverSecret", "nosecret")
  val maxJsonPostSize = System.getProperty("maxJsonPostSize", "100000").toInt

  /** Список доменов через запятую, с которых разрешено делать запросы к файловому серверу.
    * Например: originDomains = example.com,other.domain.org
    * Поддомены этих доменов также допустимы.
    */
  val originDomains: Vector[String] = System.getProperty("originDomains") match {
    case null => Vector.empty
    case s => StringUtils.split(s, ',').toVector
  }
  if (Profile.isProd) require(originDomains.nonEmpty)
}
