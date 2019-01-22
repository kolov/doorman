package com.akolov.doorman

import cats.data.{Kleisli, OptionT}
import cats.effect.{ContextShift, IO, Timer}
import com.akolov.doorman.core.{JwtIssuer, SessionManager, UserAndCookie, UserService}
import com.auth0.jwt.algorithms.Algorithm
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, CORSConfig}

import cats.implicits._
import cats.effect.Sync
import org.http4s.HttpApp
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, CORSConfig}

import scala.concurrent.duration._

object ServerConfig {

  val corsConfig: CORSConfig =
    CORSConfig(
      anyOrigin = false,
      allowedOrigins = Set("http://localhost:8000"),
      allowCredentials = true,
      maxAge = 1.day.toSeconds
    )


  val jwtIssuer = new JwtIssuer("thisapp", Algorithm.HMAC256("dsdfdsfdsfdsf"))

  val userService: Kleisli[OptionT[IO, ?], Option[String], UserAndCookie[AppUser]] = new UserService(jwtIssuer).userService

  val sessionManager = new SessionManager[IO, AppUser](userService)

  val helloWorldService = new HelloWorldService[IO, AppUser](sessionManager)
  val oauthService = new OauthService[IO]

  val app =  Router(
    "/api/v1" -> CORS(helloWorldService.routes <+> oauthService.routes, corsConfig),
  ).orNotFound

  def theService(implicit timer: Timer[IO], cs: ContextShift[IO]) =
    IO.pure(app)

}
