package com.akolov.doorman

import cats._
import cats.data._
import cats.implicits._
import cats.effect.{ContextShift, IO, Timer}
import com.akolov.doorman.core.{DoormanClient, OauthConfig, SessionManager}
import com.typesafe.config.{Config, ConfigFactory, ConfigList, ConfigObject}
import org.http4s._
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, CORSConfig}

import org.http4s.dsl.io._, org.http4s.implicits._
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

  val doormanClient : DoormanClient[IO, AppUser] =  SimpleDoormanClient


  val sessionManager = new SessionManager[IO, AppUser](doormanClient)


  def oauthService(implicit cs: ContextShift[IO]): Kleisli[IO, Config, OauthService[IO, AppUser]] =
    for {
      config <- data.Kleisli.ask[IO, Config]
      oauthEntries = AppConfig.oauthEntries(config)
      client = BlazeClientBuilder[IO](global).resource
      service = new OauthService[IO, AppUser](oauthEntries, client, doormanClient, sessionManager)
    } yield service


  implicit class KleisliResponseOps[F[_] : Functor, A](self: Kleisli[OptionT[F, ?], A, Response[F]]) {
    def orNotFound: Kleisli[F, A, Response[F]] =
      Kleisli(a => self.run(a).getOrElse(Response.notFound))
  }

  def theService(implicit timer: Timer[IO], cs: ContextShift[IO]) =
    for {
      oauthService <- oauthService
      helloWorldService = new HelloWorldService[IO, AppUser](sessionManager)
      router = Router(
        "/api/v1" -> CORS(helloWorldService.routes <+> oauthService.routes, corsConfig),
      ).orNotFound
    } yield router

}

object AppConfig {


  val config = ConfigFactory.load()

  // FIXME: use some config lib like cactus

  def oauthEntries(config: Config): Map[String, OauthConfig] = {
    val oauthConfigs = config.getValue("oauth").asInstanceOf[ConfigObject]
    val entries = oauthConfigs.keySet.asScala.map { name =>
      val configObject = oauthConfigs.get(name).asInstanceOf[ConfigObject]
      val g = configObject.toConfig
      (name, OauthConfig
      (userAuthorizationUri = g.getString("userAuthorizationUri"),
        accessTokenUri = g.getString("accessTokenUri"),
        userInfoUri = g.getString("userInfoUri"),
        clientId = g.getString("clientId"),
        clientSecret = g.getString("clientSecret"),
        scope = configObject.get("scope").asInstanceOf[ConfigList].iterator().asScala.toList.map(_.unwrapped.toString),
        redirectUrl = g.getString("redirectUrl")))
    }
    entries.toMap
  }


}
