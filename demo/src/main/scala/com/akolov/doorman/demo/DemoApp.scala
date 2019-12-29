package com.akolov.doorman.demo

import java.util.concurrent.Executors

import cats.data.Kleisli
import cats.effect.{Blocker, ContextShift, IO, Timer}
import cats.implicits._
import com.akolov.doorman.core.{OAuthProviderConfig, SessionManager}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.{Request, Response}

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._

trait ProvidersLookup {
  def forId(provider: String): Option[OAuthProviderConfig]
}

class DemoApp(doormanConfig: ProvidersLookup)(implicit timer: Timer[IO], cs: ContextShift[IO]) {

  val corsConfig: CORSConfig =
    CORSConfig(
      anyOrigin = false,
      allowedOrigins = Set("http://localhost:8080"),
      allowCredentials = true,
      maxAge = 1.day.toSeconds
    )

  val blockingEC = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  implicit val blocker = Blocker.liftExecutionContext(blockingEC)

  lazy val usersManager = DemoUserManager
  lazy val sessionManager = SessionManager(usersManager)

  lazy val httpClient = BlazeClientBuilder[IO](global).resource
  lazy val oauthService = new OauthService(doormanConfig, httpClient, usersManager)
  lazy val demoService = new DemoService(sessionManager)

  lazy val service: Kleisli[IO, Request[IO], Response[IO]] =
    CORS(demoService.routes <+> oauthService.routes, corsConfig).orNotFound

}
