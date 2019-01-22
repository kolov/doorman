package com.akolov.doorman

import cats.effect.Effect
import io.circe.Json
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl

class OauthService[F[_] : Effect] extends Http4sDsl[F] {

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "login" =>
      Ok(Json.obj("message" -> Json.fromString(s"login started")))
  }


}
