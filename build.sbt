ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.7.2"

ThisBuild / scalacOptions ++= Seq("-Xmax-inlines", "128")

lazy val root = project in file(".") settings(name := "usdt")

libraryDependencies += "com.typesafe" % "config" % "1.4.4"

libraryDependencies += "org.scalatest" % "scalatest_3" % "3.2.19"

libraryDependencies += "com.softwaremill.quicklens" % "quicklens_3" % "1.9.12"

libraryDependencies += "io.reactivex.rxjava3" % "rxjava" % "3.1.11"

libraryDependencies += "org.web3j" % "core" % "5.0.0"

libraryDependencies += "com.google.guava" % "guava" % "33.4.8-jre"

libraryDependencies += "org.java-websocket" % "Java-WebSocket" % "1.6.0"

libraryDependencies ++= Seq(
  "io.circe" % "circe-generic_3" % "0.14.14",
  "io.circe" % "circe-parser_3" % "0.14.14",
  "io.circe" %% "circe-config" % "0.10.2",
)

libraryDependencies ++= Seq(
  "ch.qos.logback" % "logback-core" % "1.5.18",
  "ch.qos.logback" % "logback-classic" % "1.5.18",
)

libraryDependencies ++= Seq(
  "com.typesafe.slick" % "slick_3" % "3.6.1",
  "com.typesafe.slick" % "slick-hikaricp_3" % "3.6.1",
  "org.postgresql" % "postgresql" % "42.7.7",
)

// Assembly

val workaround: Unit = {
  sys.props += "packaging.type" -> "jar"
}

assembly / assemblyMergeStrategy := {
  case n if n.contains("djl") => MergeStrategy.first
  case n if n.startsWith("META-INF") => MergeStrategy.discard
  case _ => MergeStrategy.first
}