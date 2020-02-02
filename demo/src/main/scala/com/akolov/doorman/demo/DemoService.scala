package com.akolov.doorman.demo

import cats.data.{EitherT, NonEmptyList}
import cats.effect.{Blocker, ContextShift, Effect, Resource}
import cats.implicits._
import com.akolov.doorman.core.{DoormanAuthMiddleware, DoormanTrackingMiddleware, OAuthProviderConfig, OauthEndpoints, UserData, UserManager}
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._
import org.http4s.CacheDirective._
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Cache-Control`, Location}
import org.http4s.{AuthedRoutes, HttpRoutes, ResponseCookie, StaticFile, Uri}

class DemoService[F[_]: Effect: ContextShift](
  userManager: UserManager[F, AppUser],
  providerLookup: String => Option[OAuthProviderConfig],
  httpClient: Resource[F, Client[F]]
)(implicit blocker: Blocker
) extends Http4sDsl[F] {

  val track = DoormanTrackingMiddleware(userManager)
  val auth = DoormanAuthMiddleware(userManager)

  object CodeMatcher extends QueryParamDecoderMatcher[String]("code")

  val oauth = OauthEndpoints[F, AppUser]()

  implicit val appUserEncoder: Encoder[AppUser] = deriveEncoder[AppUser]

  val routes =
    HttpRoutes.of[F] {
      case GET -> Root / "login" / providerId =>
        providerLookup(providerId)
          .flatMap { config =>
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
          }
          .getOrElse(BadRequest(s"Bad or missing configuration for $providerId"))
      case POST -> Root / "logout" =>
        MovedPermanently(Location(Uri.unsafeFromString("/index.html")))
          .map(_.removeCookie(userManager.cookieName))
    } <+>
      track(
        HttpRoutes.of[F] {
          case GET -> Root =>
            TemporaryRedirect(Location(Uri.unsafeFromString("/index.html")))
          case GET -> Root / "index.html" =>
            StaticFile.fromResource("/web/index.html", blocker).getOrElseF(NotFound())

        }
      ) <+>
      auth(
        AuthedRoutes.of[AppUser, F] {
          case GET -> Root / "userinfo" as user =>
            Ok(user.asJson, `Cache-Control`(NonEmptyList(`no-cache`(), List(`no-store`, `must-revalidate`))))
          case GET -> Root / "oauth" / "login" / providerId :? CodeMatcher(code) as user =>
            handleCallback(providerId, code, user)
        }
      )

  def handleCallback(providerId: String, code: String, user: AppUser) = {

    val result =
      for {
        config <- EitherT.fromOption[F](providerLookup(providerId), "Unknown provider")
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
          name = userManager.cookieName,
          content = cookieContent,
          path = Some("/")
        )
        MovedPermanently(Location(Uri.unsafeFromString("/index.html")))
          .map(_.addCookie(respCookie))
    }

  }

}
