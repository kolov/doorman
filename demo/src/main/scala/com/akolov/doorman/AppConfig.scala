package com.akolov.doorman

import com.akolov.doorman.core.{OAuthProviderConfig, ProvidersLookup}
import com.typesafe.config.{Config, ConfigFactory}
import pureconfig.ConfigReader.Result
import pureconfig._
import pureconfig.generic.auto._

object AppConfig {

  private lazy val config: Config = ConfigFactory.load
  private lazy val configSource = ConfigSource.fromConfig(config)

  case class OAuthConfig(oauthProviders: Map[String, OAuthProviderConfig])

  def demoAppConfig: Result[ProvidersLookup] =
    configSource.load[OAuthConfig].map { oAuthConfig =>
      new ProvidersLookup {
        override def forId(provider: String): Option[OAuthProviderConfig] =
          oAuthConfig.oauthProviders.get(provider)
      }
    }

}
