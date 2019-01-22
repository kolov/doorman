package com.akolov.doorman

import cats.effect.Effect
import com.akolov.doorman.core.SessionManager
import io.circe.Json
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.{AuthedService, HttpRoutes}

class HelloWorldService[F[_] : Effect, User](sessionManager: SessionManager[F, User])
  extends Http4sDsl[F] {

  val routes: HttpRoutes[F] = sessionManager.middleware(
    AuthedService {
      case GET -> Root / "hello" / name as user =>
        Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}, ${user}")))
    }
  )

}
