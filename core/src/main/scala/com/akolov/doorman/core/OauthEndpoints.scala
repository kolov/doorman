package com.akolov.doorman.core

import cats._
import cats.implicits._
import cats.data._
import cats.effect._
import com.akolov.doorman.core.logic.LoginLogic
import io.circe.{JsonObject}
import org.http4s.CacheDirective.`no-cache`
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Cache-Control`, `Content-Type`, Accept, Authorization, Location}
import org.http4s.{AuthScheme, Credentials, EntityDecoder, Headers, MediaType, Query, Request, Response, Uri}
import io.circe.syntax._

case class OAuthProviderConfig(
  userAuthorizationUri: String,
  accessTokenUri: String,
  userInfoUri: String,
  clientId: String,
  clientSecret: String,
  scope: Iterable[String],
  redirectUrl: String
)

//sealed trait UserField extends Serializable
//case class StringField(value: String) extends UserField
//case class MapField(values: Map[String, UserField]) extends UserField
//case class ListField(values: List[UserField]) extends UserField

case class UserData(data: Map[String, String])

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
          base <- EitherT.fromEither[F](
                   Uri.fromString(config.accessTokenUri).leftMap(e => ConfigurationError(e.message))
                 )
          uri = Uri(
            base.scheme,
            base.authority,
            base.path,
            Query(
              ("redirect_uri", Some(config.redirectUrl)),
              ("client_id", Some(config.clientId)),
              ("client_secret", Some(config.clientSecret)),
              ("code", Some(code)),
              ("grant_type", Some("authorization_code"))
            ),
            base.fragment
          )

          request = Request[F](
            method = POST,
            uri = uri,
            headers = Headers(Accept(MediaType.application.json))
          )
          resp <- EitherT.liftF[F, DoormanError, JsonObject](clientResource.use { client =>
                   println(s"Calling1 $uri")
                   client.expect[JsonObject](request)
                 })
          access_token <- EitherT.fromOption[F]({
                           val x = resp
                             .toMap
                             .get("access_token")
                             .flatMap(_.asString)
                           println(s"CCC=$x")
                           x

                         }, NoAccessTokenInResponse())
          uriUser <- EitherT.fromEither[F](
                      Uri.fromString(config.userInfoUri).leftMap(e => ConfigurationError(e.message))
                    )
          respUser <- EitherT.liftF[F, DoormanError, JsonObject] {
                       clientResource.use { client =>
                         println(s"Calling $uri")
                         client.expect[JsonObject](
                           Request[F](
                             method = GET,
                             uri = uriUser,
                             headers = Headers(
                               Accept(MediaType.application.json),
                               Authorization(
                                 Credentials.Token(AuthScheme.Bearer, access_token)
                               )
                             )
                           )
                         )
                       }
                     }
          userMap <- EitherT.fromEither[F](Map[String, String]().asRight)

        } yield UserData(userMap)

        e.value
      }

    }
}
