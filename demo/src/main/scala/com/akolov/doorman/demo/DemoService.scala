package com.akolov.doorman.demo

import cats.Monad
import cats.data.{EitherT, NonEmptyList}
import cats.effect.*
import cats.effect.kernel.Sync
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import com.akolov.doorman.core.*
import com.akolov.doorman.demo.AppUser
import io.circe.*
import io.circe.generic.auto._
import org.http4s.*
import org.http4s.CacheDirective.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Location, `Cache-Control`}
import io.circe.syntax._

import scala.io.Source

class DemoService[F[_]](
  userManager: UserManager[F, AppUser],
  providerLookup: String => Option[OAuthProviderConfig],
  httpClient: Resource[F, Client[F]]
)(using Concurrent[F])
    extends Http4sDsl[F] {

  val f1 = implicitly[Concurrent[F]]
  val f2 = implicitly[Monad[F]]

  private val cookieConfig: CookieConfig = CookieConfig(name = "demo-user", path = Some("/"))
  val track = DoormanTrackingMiddleware(userManager, cookieConfig)
  val auth = DoormanAuthMiddleware(userManager, cookieConfig)

  object CodeMatcher extends QueryParamDecoderMatcher[String]("code")

  val oauth = OAuthEndpoints[F]()

  val routes =
    HttpRoutes.of[F] {
      case GET -> Root / "login" / providerId =>
        providerLookup(providerId).flatMap { config =>
          oauth
            .login(config)
            .toOption
            .map { uri =>
              MovedPermanently(
                location = Location(uri),
                body = "",
                headers = `Cache-Control`(NonEmptyList(`no-cache`(), Nil))
              )
            }
        }.getOrElse(BadRequest(s"Bad or missing configuration for $providerId"))
      case POST -> Root / "logout" =>
        MovedPermanently(Location(Uri.unsafeFromString("/index.html")))
          .map(_.removeCookie(cookieConfig.name))
    } <+>
      track(
        HttpRoutes.of[F] {
          case GET -> Root =>
            TemporaryRedirect(Location(Uri.unsafeFromString("/index.html")))
          case GET -> Root / "index.html" =>
            val body = Source.fromResource("/web/index.html").getLines().mkString("\n")
//            StaticFile
//              .fromResource[F]("/web/index.html")
//              .getOrElseF(NotFound())
            Ok(body)
        }
      ) <+>
      auth(
        AuthedRoutes
          .of[AppUser, F] {
            case GET -> Root / "userinfo" as user =>
              Ok(user.asJson, `Cache-Control`(NonEmptyList(`no-cache`(), List(`no-store`, `must-revalidate`))))
            case GET -> Root / "oauth" / "login" / providerId :? CodeMatcher(code) as user =>
              handleCallback(providerId, code, user)
          }
      )

  def handleCallback(providerId: String, code: String, user: AppUser) = {
    val result =
      for {
        config <- EitherT
          .fromOption[F](providerLookup(providerId), "Unknown provider")
        result <- EitherT(httpClient.use { client =>
          oauth.callback(config, code, client).map(_.leftMap(_.toString))
        })
      } yield result

    result.value.handleError(e => Left(e.toString)).flatMap {
      case Left(error) => Ok(s"Error during OAuth: $error")
      case Right(userData) =>
        val appUser: AppUser = user.identified(userData)
        val cookieContent = userManager.userToCookie(appUser)

        val respCookie = ResponseCookie(
          name = cookieConfig.name,
          content = cookieContent,
          path = Some("/")
        )
        MovedPermanently(Location(Uri.unsafeFromString("/index.html")))
          .map(_.addCookie(respCookie))
    }
  }
}
