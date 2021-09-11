package com.akolov.doorman.demo

import com.akolov.doorman.core.OAuthProviderConfig
import com.typesafe.config.{Config, ConfigFactory}
import pureconfig._
import pureconfig.ConfigReader.Result
import pureconfig.generic.derivation.default._
object AppConfig {

  private lazy val config: Config = ConfigFactory.load
  private lazy val configSource = ConfigSource.fromConfig(config)

  given dd[A](using r: ConfigReader[A]): ConfigReader[Map[String, A]] with {
    def from(cur: ConfigCursor): ConfigReader.Result[A] = ???
  }


  case class OAuthConfig(oauthProviders: Map[String, OAuthProviderConfig]) derives ConfigReader

  val demoAppConfig: Result[OAuthConfig] = configSource.load[OAuthConfig]

}
