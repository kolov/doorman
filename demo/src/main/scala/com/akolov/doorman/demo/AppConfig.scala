package com.akolov.doorman.demo

import com.akolov.doorman.core.OAuthProviderConfig
import com.typesafe.config.{Config, ConfigFactory}
import pureconfig._
import pureconfig.ConfigReader.Result
import pureconfig.generic.derivation.default._


object AppConfig {

  trait XOrd[T]:
    def compare(x: T, y: T): Int
    extension (x: T) def <(y: T) = compare(x, y) < 0
    extension (x: T) def >(y: T) = compare(x, y) > 0

    given intOrd: XOrd[Int] with
      def compare(x: Int, y: Int) =
        if x < y then -1 else if x > y then +1 else 0

    given [A](using r: ConfigReader[A]): ConfigReader[Map[String, A]] with
      def from(cur: ConfigCursor): ConfigReader.Result[A] = ???

    private lazy val config: Config = ConfigFactory.load
    private lazy val configSource = ConfigSource.fromConfig(config)

    case class OAuthConfig(oauthProviders: Map[String, OAuthProviderConfig]) derives ConfigReader

    val demoAppConfig: Result[OAuthConfig] = configSource.load[OAuthConfig]

}
