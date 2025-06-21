import sbt.*
object Dependencies {

  lazy val logLib = Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
    "ch.qos.logback" % "logback-classic" % "1.5.18",
  )

  lazy val httpClient = {
    val version = "4.0.8"
    Seq(
      "com.softwaremill.sttp.client4" %% "core" % version,
      "com.softwaremill.sttp.client4" %% "circe" % version,
      "com.softwaremill.sttp.client4" %% "pekko-http-backend" % version,
      "com.softwaremill.sttp.client4" %% "slf4j-backend" % version,
      "org.bouncycastle" % "bcpkix-jdk18on" % "1.81",
    )
  }

  lazy val pekkoHttp = {
    val pekkoHttpVersion = "1.0.1" // Or the specific version you intend to use
    val pekkoVersion = "1.0.3" // Or the specific version for Pekko core
    Seq(
      "org.apache.pekko" %% "pekko-http" % pekkoHttpVersion,
      "org.apache.pekko" %% "pekko-stream" % pekkoVersion,
      "org.apache.pekko" %% "pekko-actor-typed" % pekkoVersion,
      // Add other Pekko modules if needed, e.g., pekko-http-spray-json for JSON support
    )
  }

  // Circe dependencies, potentially for JSON handling within Pekko HTTP if not using pekko-http-spray-json
  lazy val circe = {
    val circeVersion = "0.14.9"
    Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser",
    ).map(_ % circeVersion) ++ Seq("io.circe" %% "circe-optics" % "0.15.1")
  }


  lazy val configLib = {
    Seq(
      // "com.typesafe" % "config" % "1.4.2",
      "io.circe" %% "circe-config" % "0.10.2"
    )
  }
}
