package com.akolov.doorman

import cats.data.NonEmptyList
import cats.effect.{ContextShift, Effect}
import cats.implicits._
import com.akolov.doorman.core.SessionManager
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import org.http4s.CacheDirective._
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Cache-Control`, Location}
import org.http4s.{AuthedRoutes, HttpRoutes, StaticFile, Uri}

import scala.concurrent.ExecutionContext

class DemoService[F[_]: Effect: ContextShift](sessionManager: SessionManager[F, AppUser])(
  implicit ec: ExecutionContext
) extends Http4sDsl[F] {

  implicit val fooEncoder: Encoder[AppUser] = deriveEncoder[AppUser]

  val routes = sessionManager.cookieMiddleware(
    HttpRoutes.of[F] {
      case GET -> Root =>
        TemporaryRedirect(Location(Uri.uri("/index.html")))
      case request @ GET -> Root / "index.html" =>
        StaticFile.fromResource("/web/index.html", ec, Some(request)).getOrElseF(NotFound())
    }
  ) <+>
    sessionManager.userProviderMiddleware(
      AuthedRoutes.of {
        case GET -> Root / "userinfo" as user =>
          println(s"Hit /userinfo, user = $user")
          Ok(user.asJson, `Cache-Control`(NonEmptyList(`no-cache`(), List(`no-store`, `must-revalidate`))))
      }
    )

}
