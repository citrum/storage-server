import sbtassembly.AssemblyPlugin.assemblySettings

val DefaultScalaVersion = "2.11.11"

// setting to make the functionality be accessible from the outside (e.g., the terminal)
val profile = SettingKey[Profile]("profile", "Uses resources for the specified profile.")

lazy val dev = inputKey[Unit]("Run NettyServerDev")
val devTask = dev := (runMain in Compile).fullInput(" server.NettyServerDev").evaluated

lazy val root = Project(id = "storage-server", base = new File("."), settings = assemblySettings ++ Seq(
  profile := Profile.prod,
  version := "0.9",
  scalaVersion := DefaultScalaVersion,

  javacOptions ++= Seq("-source", "1.8", "-encoding", "UTF-8"),
  javacOptions in doc := Seq("-source", "1.8"),

  sources in doc in Compile := List(), // Выключить генерацию JavaDoc, ScalaDoc

  scalaSource in Compile := baseDirectory.value / "src",
  resourceDirectory in Compile := baseDirectory.value / "conf",
  scalaSource in Test := baseDirectory.value / "test",
  unmanagedBase := baseDirectory.value / "unmanaged/jars",

  libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.1",
  libraryDependencies += "io.netty" % "netty-all" % "4.0.36.Final",
  libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.2.1",
  libraryDependencies += "com.fasterxml.jackson.core" % "jackson-databind" % "2.4.5", // Работа с json
  libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.4.5-1" exclude("com.google.guava", "guava"), // Работа с json
  libraryDependencies += "com.intellij" % "annotations" % "12.0", // для интеграции IDEA language injection
  libraryDependencies += "com.google.guava" % "guava" % "18.0",
  libraryDependencies += "net.coobird" % "thumbnailator" % "0.4.8",
  libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0-M15" % "test",
  libraryDependencies += "com.getsentry.raven" % "raven-logback" % "7.1.0", // Sentry plugin for log processing
  libraryDependencies += "com.jsuereth" %% "scala-arm" % "1.4",
  libraryDependencies += "org.glassfish.external" % "opendmk_jmxremote_optional_jar" % "1.0-b01-ea",

  mainClass in assembly := (profile.value match {
    case Profile.local => Some("server.NettyServerDev")
    case Profile.jenkins => Some("server.NettyServerJenkins")
    case Profile.prod => Some("server.NettyServer")
  }),
  assemblyJarName in assembly := "../storage-server.jar",

  devTask
))
