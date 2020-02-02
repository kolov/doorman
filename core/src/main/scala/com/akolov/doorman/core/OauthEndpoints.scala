package com.akolov.doorman.core

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import com.akolov.doorman.core.logic.LoginLogic
import io.circe.JsonObject
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Accept, Authorization}
import org.http4s._

case class OAuthProviderConfig(
  userAuthorizationUri: String,
  accessTokenUri: String,
  userInfoUri: String,
  clientId: String,
  clientSecret: String,
  scope: Iterable[String],
  redirectUrl: String
)

case class UserData(attrs: Map[String, String])

/**
  * This provides the necessary endpoints to handle OAuth login and callback.
  * They need to be mapped to
  * routes. See the demo application for an example
  */

trait OauthEndpoints[F[_], User] extends Http4sClientDsl[F] {
  // Builds a url to redirect the user to for authentication
  def login(config: OAuthProviderConfig): Either[DoormanError, Uri]
  // handles teh OAuth2 callback
  def callback(config: OAuthProviderConfig, code: String): F[Either[DoormanError, UserData]]
}

object OauthEndpoints {

  def apply[F[_]: Effect: Monad, User](clientResource: Resource[F, Client[F]]) =
    new OauthEndpoints[F, User] with Http4sDsl[F] {

      implicit val jsonObjectDecoder: EntityDecoder[F, JsonObject] =
        jsonOf[F, JsonObject]

      def login(config: OAuthProviderConfig): Either[DoormanError, Uri] =
        LoginLogic.login(config)

      def callback(config: OAuthProviderConfig, code: String): F[Either[DoormanError, UserData]] = {
        val e: EitherT[F, DoormanError, UserData] = for {
          uri <- EitherT.fromEither[F](
                  Uri.fromString(config.accessTokenUri).leftMap(e => ConfigurationError(e.message))
                )
          request = POST(
            UrlForm(
              ("redirect_uri", config.redirectUrl),
              ("client_id", config.clientId),
              ("client_secret", config.clientSecret),
              ("code", code),
              ("grant_type", "authorization_code")
            ),
            uri,
            Accept(MediaType.application.json)
          )

          resp <- EitherT.liftF[F, DoormanError, JsonObject](clientResource.use { client =>
                   client.expect[JsonObject](request)
                 })
          access_token <- EitherT.fromOption[F]({
                           resp
                             .toMap
                             .get("access_token")
                             .flatMap(_.asString)

                         }, NoAccessTokenInResponse())
          uriUser <- EitherT.fromEither[F](
                      Uri.fromString(config.userInfoUri).leftMap(e => ConfigurationError(e.message))
                    )
          respUser <- EitherT.liftF[F, DoormanError, JsonObject] {
                       clientResource.use { client =>
                         client.expect[JsonObject](
                           Request[F](
                             method = GET,
                             uri = uriUser,
                             headers = Headers.of(
                               Accept(MediaType.application.json),
                               Authorization(
                                 Credentials.Token(AuthScheme.Bearer, access_token)
                               )
                             )
                           )
                         )
                       }
                     }
          userMap <- EitherT.pure[F, DoormanError](respUser.toMap.mapValues(_.toString))

        } yield UserData(userMap)

        e.value
      }

    }
}
