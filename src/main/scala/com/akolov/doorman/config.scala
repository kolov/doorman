package com.akolov.doorman

import cats.data.{Kleisli, OptionT}
import cats.effect.{ContextShift, IO, Timer}
import com.akolov.doorman.core.{JwtIssuer, SessionManager, UserService}
import com.auth0.jwt.algorithms.Algorithm
import org.http4s.server.middleware.CORSConfig

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

  val userService: Kleisli[OptionT[IO, ?], Option[String], AppUser] = new UserService(jwtIssuer).userService

  val sessionManager = new SessionManager[IO, AppUser](userService)

  val helloWorldService = new HelloWorldService[IO, AppUser](sessionManager)

  def theService(implicit timer: Timer[IO], cs: ContextShift[IO]) =
    IO.pure(new AllServices[IO, AppUser](helloWorldService, corsConfig))

}
