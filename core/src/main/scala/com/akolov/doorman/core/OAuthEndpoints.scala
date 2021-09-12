package com.akolov.doorman.core

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import io.circe.JsonObject
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Accept, Authorization}

// This is needed for every OAuth2 provider
case class OAuthProviderConfig(
  userAuthorizationUri: String,
  accessTokenUri: String,
  userInfoUri: String,
  clientId: String,
  clientSecret: String,
  scope: List[String],
  redirectUrl: String
)

import org.http4s._, org.http4s.dsl.io._, org.http4s.implicits._

// on successful authentication, we'll get this from the Oauth2 provider
case class UserData(attrs: Map[String, String])

/** This provides the necessary endpoints to handle OAuth login and callback. They need to be mapped to routes. See the
  * demo application for an example
  */
trait OAuthEndpoints[F[_]] {
  // Builds a url to redirect the user to for authentication
  def login(config: OAuthProviderConfig): Either[DoormanError, Uri]
  // handles teh OAuth2 callback
  def callback(config: OAuthProviderConfig, code: String, client: Client[F]): F[Either[DoormanError, UserData]]
}

object OAuthEndpoints {

  def apply[F[_]: Concurrent]() =
    new OAuthEndpoints[F] with Http4sDsl[F] with Http4sClientDsl[F] {

      def login(config: OAuthProviderConfig): Either[DoormanError, Uri] =
        for
          base <- Uri
            .fromString(config.userAuthorizationUri)
            .leftMap(e => ConfigurationError(e.message))
          uri = Uri(
            base.scheme,
            base.authority,
            base.path,
            Query(
              ("redirect_uri", Some(config.redirectUrl)),
              ("client_id", Some(config.clientId)),
              ("response_type", Some("code")),
              ("scope", Some(config.scope.mkString(" ")))
            ),
            base.fragment
          )
        yield uri

      def callback(config: OAuthProviderConfig, code: String, client: Client[F]): F[Either[DoormanError, UserData]] = {
        implicit val jsonObjectDecoder: EntityDecoder[F, JsonObject] =
          jsonOf[F, JsonObject]

        val e: EitherT[F, DoormanError, UserData] = for
          uri <- EitherT.fromEither[F](
            Uri
              .fromString(config.accessTokenUri)
              .leftMap(e => ConfigurationError(e.message))
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

          resp <- EitherT.liftF[F, DoormanError, JsonObject](client.expect[JsonObject](request))
          access_token <- EitherT.fromOption[F](
            {
              resp.toMap
                .get("access_token")
                .flatMap(_.asString)
            },
            NoAccessTokenInResponse())
          uriUser <- EitherT.fromEither[F](
            Uri
              .fromString(config.userInfoUri)
              .leftMap(e => ConfigurationError(e.message))
          )
          respUser <- EitherT.liftF[F, DoormanError, JsonObject] {
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
          userMap <- EitherT.pure[F, DoormanError](jsonToMap(respUser))
        yield UserData(userMap)

        e.value
      }
    }

  // without it, strings get extra quotes
  def jsonToMap(m: JsonObject): Map[String, String] =
    m.toMap.map { case (k, v) =>
      (
        k,
        v.fold(
          v.toString,
          _.toString,
          _.toString,
          s => s,
          _.toString,
          _.toString
        ))
    }
}
