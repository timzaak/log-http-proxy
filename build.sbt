val scala3Version = "3.7.1"
import Dependencies.*

lazy val root = project
  .in(file("."))
  .settings(
    name := "log-http-proxy",
    version := "0.2.3",
    scalaVersion := scala3Version,
    libraryDependencies ++=
      tapir ++ logLib ++ configLib ++
        Seq(
          "com.lihaoyi" %% "mainargs" % "0.7.6",
          "dnsjava" % "dnsjava" % "3.6.3",
          "org.scalameta" %% "munit" % "1.1.1" % Test
        )
  )
  .enablePlugins(JavaServerAppPackaging)
