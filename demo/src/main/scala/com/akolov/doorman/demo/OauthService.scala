package com.akolov.doorman.demo

import cats.data._
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
  userManager: UserManager[F, User] with OAuthUserManager[F, User]
) extends Http4sDsl[F] {

  object CodeMatcher extends QueryParamDecoderMatcher[String]("code")

  val oauth = OauthEndpoints[F, User](httpClient, userManager)

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root / "login" / providerId =>
      oauthProviders
        .forId(providerId)
        .flatMap { config =>
          oauth
            .login(config)
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

    case GET -> Root / "oauth" / "login" / providerId :? CodeMatcher(code) =>
      handleCallback(providerId, code)
  }

  def handleCallback(providerId: String, code: String) = {
    val result = for {
      config <- EitherT.fromOption[F](oauthProviders.forId(providerId), "Unknown provider")
      result <- EitherT(oauth.callback(providerId, config, code))
    } yield result

    result.value.flatMap {
      case Left(error) => Ok(s"Error during OAuth: $error")
      case Right(user) =>
        val cookieContent = userManager.userToCookie(user)

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
