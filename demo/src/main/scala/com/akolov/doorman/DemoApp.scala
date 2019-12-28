package com.akolov.doorman

import cats.data.Kleisli
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import com.akolov.doorman.core.{ProvidersLookup, SessionManager, UsersManager}
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits._
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.{Request, Response}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class DemoApp(doormanConfig: ProvidersLookup)(implicit timer: Timer[IO], cs: ContextShift[IO]) {

  val corsConfig: CORSConfig =
    CORSConfig(
      anyOrigin = false,
      allowedOrigins = Set("http://localhost:8000"),
      allowCredentials = true,
      maxAge = 1.day.toSeconds
    )

  lazy val doormanClient: UsersManager[IO, AppUser] = SimpleUsersManager
  lazy val httpClient = BlazeClientBuilder[IO](global).resource
  lazy val sessionManager = SessionManager(doormanClient, doormanConfig)
  lazy val oauthService = new OauthService(doormanConfig, httpClient, doormanClient, sessionManager)
  lazy val demoService = new DemoService(sessionManager)

  lazy val service: Kleisli[IO, Request[IO], Response[IO]] =
    CORS(demoService.routes <+> oauthService.routes, corsConfig).orNotFound

}
