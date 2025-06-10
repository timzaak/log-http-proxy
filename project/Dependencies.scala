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
      "org.bouncycastle" % "bcpkix-jdk18on" % "1.80",
    )
  }

  lazy val tapir = {
    val version = "1.11.33"
    val circeVersion = "0.14.9"
    Seq(
      "io.circe" %% "circe-core",
      "io.circe" %% "circe-generic",
      "io.circe" %% "circe-parser",
    ).map(_ % circeVersion) ++
      Seq(
        "io.circe" %% "circe-optics" % "0.15.0",
        // "com.softwaremill.sttp.tapir" %% "tapir-netty-server" % version,
        "com.softwaremill.sttp.tapir" %% "tapir-pekko-http-server" % version,
        "com.softwaremill.sttp.tapir" %% "tapir-json-circe" % version,
        // docs
        "com.softwaremill.sttp.tapir" %% "tapir-redoc-bundle" % version,
        // static file
        "com.softwaremill.sttp.tapir" %% "tapir-files" % version,
        "com.softwaremill.sttp.tapir" %% "tapir-enumeratum" % version,
        // monitor, you could use openTelemetry java agent to solve metrics collector.
        // "com.softwaremill.sttp.tapir" %% "tapir-opentelemetry-metrics" % version,
      )
  }

  lazy val configLib = {
    Seq(
      // "com.typesafe" % "config" % "1.4.2",
      "io.circe" %% "circe-config" % "0.10.1"
    )
  }
}
