package com.akolov.doorman

import cats.implicits._
import cats.effect.{ContextShift, Effect}
import com.akolov.doorman.core.SessionManager
import io.circe.Json
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location
import org.http4s.{AuthedService, HttpRoutes, StaticFile, Uri}

import io.circe._, io.circe.generic.semiauto._, io.circe.syntax._
import scala.concurrent.ExecutionContext

class DemoService[F[_]: Effect: ContextShift](sessionManager: SessionManager[F, AppUser])(implicit ec: ExecutionContext)
    extends Http4sDsl[F] {

  implicit val fooEncoder: Encoder[AppUser] = deriveEncoder[AppUser]

  val routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root =>
      // Not every response must be Ok using a EntityEncoder: some have meaning only for specific types
      TemporaryRedirect(Location(Uri.uri("/index.html")))

    case request @ GET -> Root / "index.html" =>
      StaticFile.fromResource("/index.html", ec, Some(request)).getOrElseF(NotFound())

  } <+>
    sessionManager.middleware(
      AuthedService {
        case GET -> Root / "userinfo" as user =>
          Ok(user.asJson)
      }
    )

}
