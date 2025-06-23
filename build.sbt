val scala3Version = "3.7.1"
import Dependencies.*

lazy val root = project
  .in(file("."))
  .settings(
    name := "log-http-proxy",
    version := "0.1.0",
    scalaVersion := scala3Version,
    mainClass := Some("Main"),
    libraryDependencies ++=
      tapir ++ logLib ++ httpClient ++ configLib ++
        Seq(
          "com.lihaoyi" %% "mainargs" % "0.7.6",
          "com.github.pathikrit" %% "better-files" % "3.9.2",
          "dnsjava" % "dnsjava" % "3.6.3",
          "org.scalameta" %% "munit" % "1.1.1" % Test
        )
  )
  .enablePlugins(JavaServerAppPackaging)
