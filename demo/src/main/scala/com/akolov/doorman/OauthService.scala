package com.akolov.doorman

import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import com.akolov.doorman.core._
import org.http4s.CacheDirective.`no-cache`
import org.http4s._
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Cache-Control`, Location}

/**
  * Endpoints needed for OAuth2
  */
class OauthService[F[_]: Effect: Sync: ContextShift, User](
  oauthProviders: ProvidersLookup,
  httpClient: Resource[F, Client[F]],
  userManager: OAuthUserManager[F, User],
  sessionManager: SessionManager[F, User]
) extends Http4sDsl[F] {

  object CodeMatcher extends QueryParamDecoderMatcher[String]("code")

  val oauth =
    new OauthEndpoints[F, User](httpClient, userManager, oauthProviders)

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root / "login" / providerId =>
      oauth
        .login(providerId)
        .map { uri =>
          MovedPermanently(
            location = Location(uri),
            body = "",
            headers = `Cache-Control`(NonEmptyList(`no-cache`(), Nil))
          )
        }
        .getOrElse(BadRequest(s"Bad or missing configuration for $providerId"))

    case POST -> Root / "logout" =>
      MovedPermanently(Location(uri"/index.html"))
        .map(_.removeCookie(userManager.cookieName))

    case GET -> Root / "oauth" / "login" / providerId :? CodeMatcher(code) =>
      val result: F[Either[String, User]] = oauth.callback(providerId, code)

      result.flatMap {
        case Left(error) => Ok(s"Error during OAuth: $error")
        case Right(user) =>
          val cookieContent = userManager.userToCookie(user)

          val respCookie = ResponseCookie(
            name = userManager.cookieName,
            content = cookieContent,
            path = Some("/")
          )
          MovedPermanently(Location(uri"/index.html"))
            .map(_.addCookie(respCookie))
      }

  }

}
