package com.timzaak.proxy

import com.typesafe.config.ConfigFactory
import io.circe.config.syntax.*
import io.circe.derivation.{ Configuration, ConfiguredCodec }
import io.circe.generic.auto.*

given Configuration = Configuration.default.withDefaults

case class AppConfig(
  dns: List[DNS] = List.empty,
  jks: Option[JKSConf] = None,
  resolver: List[String] = List.empty,
  viewerPort: Option[Int] = None,
  selfSignedCert: Option[String] = None
) derives ConfiguredCodec

object AppConfig {
  def load(): AppConfig = ConfigFactory.load().as[AppConfig].fold(throw _, identity)
}

case class DNS(
  ip: String,
  domain: String
)
