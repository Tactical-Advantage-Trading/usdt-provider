ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.2"

ThisBuild / scalacOptions ++= Seq("-Xmax-inlines", "128")

lazy val root = project in file(".") settings(name := "usdt")

libraryDependencies += "com.typesafe" % "config" % "1.4.5"

libraryDependencies += "org.scalatest" % "scalatest_3" % "3.2.19"

libraryDependencies += "com.softwaremill.quicklens" % "quicklens_3" % "1.9.12"

libraryDependencies += "io.reactivex.rxjava3" % "rxjava" % "3.1.12"

libraryDependencies += "org.web3j" % "core" % "5.0.1"

libraryDependencies += "com.google.guava" % "guava" % "33.5.0-jre"

libraryDependencies += "org.java-websocket" % "Java-WebSocket" % "1.6.0"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.5.18"

libraryDependencies ++= Seq(
  "io.circe" % "circe-generic_3" % "0.14.15",
  "io.circe" % "circe-parser_3" % "0.14.15",
  "io.circe" %% "circe-config" % "0.10.2",
)

libraryDependencies ++= Seq(
  "com.typesafe.slick" % "slick_3" % "3.6.1",
  "com.typesafe.slick" % "slick-hikaricp_3" % "3.6.1",
  "org.postgresql" % "postgresql" % "42.7.8",
)

import sbtassembly.AssemblyPlugin.autoImport.*
import sbtassembly.{MergeStrategy, PathList}

assembly / assemblyMergeStrategy := {
  val old = (assembly / assemblyMergeStrategy).value

  (path: String) => path match {
    case "module-info.class" => MergeStrategy.discard
    case PathList("META-INF", "versions", _*) => MergeStrategy.discard

    case PathList("META-INF", "DEPENDENCIES") => MergeStrategy.discard
    case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
    case PathList("META-INF", "LICENSE.txt") => MergeStrategy.discard
    case PathList("META-INF", "INDEX.LIST") => MergeStrategy.discard
    case PathList("META-INF", "NOTICE.txt") => MergeStrategy.discard
    case PathList("META-INF", "LICENSE") => MergeStrategy.discard
    case PathList("META-INF", "NOTICE") => MergeStrategy.discard

    case PathList("META-INF", "services", _*) => MergeStrategy.concat
    case "application.conf" => MergeStrategy.concat
    case "reference.conf" => MergeStrategy.concat
    case other => old(other)
  }
}