val scala3Version = "3.6.4"
import Dependencies.*
lazy val root = project
  .in(file("."))
  .settings(
    name := "https-proxy",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++=
      tapir ++ logLib ++ httpClient ++ configLib ++
        Seq(

          //"com.github.monkeywie" % "proxyee" % "1.7.6",
          "org.scalameta" %% "munit" % "1.1.0" % Test)
  )
