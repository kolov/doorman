package com.akolov.doorman.demo

import com.akolov.doorman.core.OAuthProviderConfig
import com.typesafe.config.{Config, ConfigFactory}
import pureconfig.ConfigReader.Result
import pureconfig._
import pureconfig.generic.auto._

object AppConfig {

  private lazy val config: Config = ConfigFactory.load
  private lazy val configSource = ConfigSource.fromConfig(config)

  case class OAuthConfig(oauthProviders: Map[String, OAuthProviderConfig])

  def demoAppConfig: Result[OAuthConfig] =
    configSource.load[OAuthConfig]

}
