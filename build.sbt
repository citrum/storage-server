val DefaultScalaVersion = "2.11.11"

lazy val dev = inputKey[Unit]("Run NettyServerDev")
val devTask = dev := (runMain in Compile).fullInput(" server.NettyServerDev").evaluated

lazy val root = Project(id = "storage-server", base = new File("."), settings = AssemblyPlugin.assemblySettings ++ Seq(
  version := "0.9.0",
  scalaVersion := DefaultScalaVersion,

  javacOptions ++= Seq("-source", "1.8", "-encoding", "UTF-8"),
  javacOptions in doc := Seq("-source", "1.8"),

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

  assemblyJarName in assembly := "../storage-server.jar",

  devTask,

  // Deploy settings
  startYear := Some(2014),
  homepage := Some(url("https://github.com/citrum/storage-server")),
  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  bintrayVcsUrl := Some("https://github.com/citrum/storage-server"),
  bintrayOrganization := Some("citrum"),
  // No Javadoc
  publishArtifact in(Compile, packageDoc) := false,
  publishArtifact in packageDoc := false,
  sources in(Compile, doc) := Nil,

  // Publish fat jar
  artifact in (Compile, assembly) := {
    val art = (artifact in (Compile, assembly)).value
    art.copy(`classifier` = Some("assembly"))
  }
))

addArtifact(artifact in (Compile, assembly), assembly)
