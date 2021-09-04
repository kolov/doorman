package com.akolov.doorman.demo

import java.util.concurrent.Executors
import cats.data.Kleisli
import cats.effect.{Blocker, ContextShift, IO, Timer}
import cats.implicits.*
import com.akolov.doorman.core.OAuthProviderConfig
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.{Request, Response}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.*

class DemoApp(providerLookup: String => Option[OAuthProviderConfig])(implicit timer: Timer[IO], cs: ContextShift[IO]) {

  val corsConfig: CORSConfig =
    CORSConfig(
      anyOrigin = false,
      allowedOrigins = Set("http://localhost:8080"),
      allowCredentials = true,
      maxAge = 1.day.toSeconds
    )

  val blockingEC: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
  implicit val blocker = Blocker.liftExecutionContext(blockingEC)

  lazy val usersManager = DemoUserManager

  lazy val httpClient = BlazeClientBuilder[IO](global).resource

  lazy val demoService = new DemoService(usersManager, providerLookup, httpClient)

  lazy val service: Kleisli[IO, Request[IO], Response[IO]] =
    CORS(demoService.routes, corsConfig).orNotFound

}
