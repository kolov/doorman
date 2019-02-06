package com.akolov.doorman

import cats._
import cats.data._
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.akolov.doorman.core.{Doorman, DoormanConfig, OauthConfig, SessionManager}
import com.typesafe.config.{Config, ConfigFactory, ConfigList, ConfigObject}
import org.http4s._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, CORSConfig}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object ServerConfig {

  val corsConfig: CORSConfig =
    CORSConfig(
      anyOrigin = false,
      allowedOrigins = Set("http://localhost:8000"),
      allowCredentials = true,
      maxAge = 1.day.toSeconds
    )

  val doormanClient: Doorman[IO, AppUser] = SimpleDoorman


  def sessionManager(implicit cs: ContextShift[IO]) =
    for {
      config <- data.Kleisli.ask[IO, Config]
      doormanConfig = AppConfig.demoConfig(config)
      sessionManager = new SessionManager[IO, AppUser](doormanClient, doormanConfig)
    } yield sessionManager


  implicit class KleisliResponseOps[A](self: Kleisli[OptionT[IO, ?], A, Response[IO]]) {
    def orNotFound: Kleisli[IO, A, Response[IO]] =
      Kleisli(a => self.run(a).getOrElse(Response.notFound))
  }

  def theService(implicit timer: Timer[IO], cs: ContextShift[IO]) =
    for {
      config <- data.Kleisli.ask[IO, Config]
      doormanConfig = AppConfig.demoConfig(config)
      client = BlazeClientBuilder[IO](global).resource
      sessionManager = new SessionManager[IO, AppUser](doormanClient, doormanConfig)
      oauthService = new OauthService[IO, AppUser](doormanConfig, client, doormanClient, sessionManager)
      helloWorldService = new DemoService[IO, AppUser](sessionManager)
      router = Router(
        "/api/v1" -> CORS(helloWorldService.routes <+> oauthService.routes, corsConfig),
      ).orNotFound
    } yield router

}

object AppConfig {


  val config = ConfigFactory.load()

  def oauthEntries(config: Config): Map[String, OauthConfig] = {
    val oauthConfigs = config.getValue("oauth").asInstanceOf[ConfigObject]
    val entries = oauthConfigs.keySet.asScala.map { name =>
      val configObject = oauthConfigs.get(name).asInstanceOf[ConfigObject]
      val g = configObject.toConfig
      (name, OauthConfig(userAuthorizationUri = g.getString("userAuthorizationUri"),
        accessTokenUri = g.getString("accessTokenUri"),
        userInfoUri = g.getString("userInfoUri"),
        clientId = g.getString("clientId"),
        clientSecret = g.getString("clientSecret"),
        scope = configObject.get("scope").asInstanceOf[ConfigList].iterator().asScala.toList.map(_.unwrapped.toString),
        redirectUrl = g.getString("redirectUrl")))
    }
    entries.toMap
  }

  def demoConfig(config: Config): DoormanConfig = new DoormanConfig {
    override val cookieName: String = "demo-auth"

    lazy val configs = oauthEntries(config)

    override def provider(provider: String): Option[OauthConfig] = configs.get(provider)
  }


}
