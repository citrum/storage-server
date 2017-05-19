package server

import java.io.{File, FileOutputStream}
import java.lang.management.ManagementFactory
import java.net.InetSocketAddress
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import java.util.{Collections, Scanner}
import javax.management.remote.{JMXConnectorServerFactory, JMXServiceURL}

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import core.{Conf, ExecutorContexts, PageLog, Profile}
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.{Channel, ChannelFuture, ChannelInitializer, ChannelOption}
import io.netty.handler.codec.http.{HttpRequestDecoder, HttpResponseEncoder}
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.ssl.{SslContext, SslContextBuilder}
import io.netty.util.ResourceLeakDetector.Level
import io.netty.util.concurrent.DefaultThreadFactory
import io.netty.util.{ResourceLeakDetector, concurrent}
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

/**
  * creates a Server implementation based Netty
  */
class NettyServer(address: String, httpPort: Int, httpsPort: Int) {

  val log = LoggerFactory.getLogger(getClass)

  var nextHandlerId = new AtomicInteger(1)

  class ServerChannelInitializer(maybeSslCtx: Option[SslContext] = None) extends ChannelInitializer[SocketChannel] {
    def initChannel(ch: SocketChannel) {
      val pipeline = ch.pipeline()

      // Add SSL handler first to encrypt and decrypt everything.
      maybeSslCtx.foreach(sslCtx => pipeline.addLast(sslCtx.newHandler(ch.alloc())))

      ///// for dev only ////pipeline.addLast("shaper", new ChannelTrafficShapingHandler(0, 50000))
      pipeline.addLast("decoder", new HttpRequestDecoder())
      pipeline.addLast("encoder", new HttpResponseEncoder())
      pipeline.addLast("handler", new NettyHandler(nextHandlerId.getAndIncrement(), https = maybeSslCtx.isDefined))
    }
  }
  // Initialize MBean
  initMbean()

  // The HTTP server channel
  val bossGroup = new NioEventLoopGroup(0, new DefaultThreadFactory("netty-boss"))
  val workerGroup = new NioEventLoopGroup(Conf.workerThreadNum, new DefaultThreadFactory("netty-worker"))

  private def createChannel(port: Int, useSsl: Boolean): Channel =
    try {
      val maybeSslCtx: Option[SslContext] =
        if (useSsl) Some {
          {
            for (certFile <- Conf.sslKeyCertChainFile; keyFile <- Conf.sslPrivateKeyFile)
              yield {
                log.info("Using certFile:" + certFile + ", keyFile:" + keyFile)
                SslContextBuilder.forServer(certFile, keyFile).build()
              }
          }.getOrElse {
            log.info("Using self-signed certificate")
            val ssc = new SelfSignedCertificate()
            SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build()
          }
        } else None

      val bootstrap = new ServerBootstrap()
        .group(bossGroup, workerGroup)
        .channel(classOf[NioServerSocketChannel])
        .childHandler(new ServerChannelInitializer(maybeSslCtx))
        .option(ChannelOption.SO_BACKLOG, 128.asInstanceOf[Integer]) // Длина очереди на соединение с сервером, see ServerSocket.bind()
        .childOption(ChannelOption.SO_KEEPALIVE, java.lang.Boolean.TRUE)
      val f: ChannelFuture = bootstrap.bind(new InetSocketAddress(address, port)).sync()
      f.channel()
    } catch {
      case e: Throwable =>
        bossGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS)
        workerGroup.shutdownGracefully(0, 60, TimeUnit.SECONDS)
        throw e
    }

  val httpChannel: Channel = createChannel(httpPort, useSsl = false)
  val httpsChannel: Channel = createChannel(httpsPort, useSsl = true)

  // Если указан successFile, то записать в него 1, сообщая демону, что сервер запустился
  Option(System.getProperty("successFile")).foreach {n => val s = new FileOutputStream(n); s.write('1'); s.close()}

  log.info("baseDir: " + Conf.baseDir)
  log.info("tmpDir: " + Conf.tmpDir)
  log.warn("--------- Started server on " + httpChannel.localAddress() + ", ssl:" + httpsChannel.localAddress().asInstanceOf[InetSocketAddress].getPort + " ---------")

  def stop() {
    log.warn("--------- Stopping server ---------")

    // Close all opened sockets
    val bgFuture: concurrent.Future[_] = bossGroup.shutdownGracefully(0, if (Profile.isProd) 10 else 1, TimeUnit.SECONDS)
    val wgFuture: concurrent.Future[_] = workerGroup.shutdownGracefully(0, if (Profile.isProd) 60 else 1, TimeUnit.SECONDS)
    ExecutorContexts.commonHandlerService.shutdown()
    ExecutorContexts.imageService.shutdown()
    httpChannel.closeFuture().sync()
    httpsChannel.closeFuture().sync()
    wgFuture.awaitUninterruptibly()

    log.info("All channels closed. Stopping application.")
    ExecutorContexts.commonHandlerService.awaitTermination(1, TimeUnit.MINUTES)
    ExecutorContexts.imageService.awaitTermination(1, TimeUnit.MINUTES)

    PageLog.logWriter.closeWriter()
    log.warn("Server stopped") // Следующая команда super.stop() закрывает логгер, поэтому сообщение стопа сервера здесь.
  }

  private def initMbean(): Unit = {
    val mBeanServer = ManagementFactory.getPlatformMBeanServer

    // To use JMXMP server you must add sbt dependency:
    // deps += "org.glassfish.external" % "opendmk_jmxremote_optional_jar" % "1.0-b01-ea"
    Option(System.getProperty("jmxmp.port")).foreach {port =>
      JMXConnectorServerFactory.newJMXConnectorServer(
        new JMXServiceURL("service:jmx:jmxmp://127.0.0.1:" + port),
        Collections.emptyMap(),
        mBeanServer
      ).start()
    }
  }
}

/**
  * bootstraps Play application with a NettyServer backend
  */
object NettyServer {

  import java.io._

  /**
    * creates a NettyServer based on the application represented by applicationPath
    *
    * @param applicationPath path to application
    */
  def createServer(applicationPath: File): Option[NettyServer] = {
    System.setProperty("java.net.preferIPv4Stack", "true")
    ResourceLeakDetector.setLevel(Level.ADVANCED) // Будем обнаруживать memory leaks
    try {
      val server = new NettyServer(Conf.httpAddress, Conf.httpPort, Conf.httpsPort)

      Runtime.getRuntime.addShutdownHook(new Thread {
        override def run() {
          server.stop()
        }
      })

      Some(server)
    } catch {
      case NonFatal(e) =>
        println("Oops, cannot start the server.")
        e.printStackTrace()
        None
    }
  }

  private def configureLogback0(): JoranConfigurator = {
    val configurator: JoranConfigurator = new JoranConfigurator
    val ctx: LoggerContext = LoggerFactory.getILoggerFactory.asInstanceOf[LoggerContext]
    configurator.setContext(ctx)
    ctx.reset()
    configurator
  }

  def configureLogback(configResourceName: String): Unit = {
    configureLogback0().doConfigure(getClass.getClassLoader.getResource(configResourceName))
  }
  def configureLogback(configFile: File): Unit = {
    configureLogback0().doConfigure(configFile)
  }

  /**
    * attempts to create a NettyServer based on either
    * passed in argument or `user.dir` System property or current directory
    */
  def main(args: Array[String]) {
    NettyServer.configureLogback(new File("logback-prod.xml"))
    createServer(new File(".")).getOrElse(sys.exit(-1))
  }
}

object NettyServerDev {
  def main(args: Array[String]) {
    NettyServer.configureLogback("logback-dev.xml")
    Profile.init(Profile.Local)
    //    ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)
    val server: NettyServer = NettyServer.createServer(new File(".")).getOrElse(sys.exit(-1))
    val s = new Scanner(System.in)
    try while (s.nextLine().nonEmpty) {}
    catch {case e: Exception => ()}
    server.stop()
    sys.exit()
  }
}

object NettyServerJenkins {
  def main(args: Array[String]) {
    NettyServer.configureLogback("logback-dev.xml")
    Profile.init(Profile.Jenkins)
    NettyServer.createServer(new File(".")).getOrElse(sys.exit(-1))
  }
}
