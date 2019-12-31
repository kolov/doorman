package com.akolov.doorman.core

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import io.circe._
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{AuthScheme, Credentials, EntityDecoder, Headers, MediaType, Query, Request, Uri, UrlForm}

/**
  * This provides the necessary endpoints to handle OAuth login and callback.
  * They need to be mapped to
  * routes. See the demo application for an example
  */

trait OauthEndpoints[F[_], User] extends Http4sClientDsl[F] {
  // Builds a url to redirect the user to for authentication
  def login(config: OAuthProviderConfig): Option[Uri]
  // handles teh OAuth2 callback
  def callback(providerId: String, config: OAuthProviderConfig, code: String): F[Either[String, User]]
}

object OauthEndpoints {

  def apply[F[_]: Effect: Monad, User](
    clientResource: Resource[F, Client[F]],
    oauthUserManager: OAuthUserManager[F, User]
  ) =
    new OauthEndpoints[F, User] with Http4sDsl[F] {

      implicit val jsonObjectDecoder: EntityDecoder[F, JsonObject] =
        jsonOf[F, JsonObject]

      def login(config: OAuthProviderConfig): Option[Uri] =
        for {
          base <- Uri.fromString(config.userAuthorizationUri).toOption
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
        } yield uri

      def callback(providerId: String, config: OAuthProviderConfig, code: String): F[Either[String, User]] = {
        val user = for {
          base <- EitherT.fromEither[F](Uri.fromString(config.accessTokenUri).leftMap(_.toString))
          uri = Uri(
            base.scheme,
            base.authority,
            base.path
          )
          _ = println(uri)

          request = POST(
            UrlForm(
              "redirect_uri" -> config.redirectUrl,
              "client_id" -> config.clientId,
              "client_secret" -> config.clientSecret,
              "code" -> code,
              "grant_type" -> "authorization_code"
            ),
            uri
          )

          resp <- EitherT.liftF[F, String, JsonObject](clientResource.use { client =>
                   client.expect[JsonObject](request)
                 })
          access_token <- EitherT.fromOption[F](
                           resp.toMap
                             .get("access_token")
                             .flatMap(_.asString),
                           "no access_token"
                         )
          uriUser <- EitherT.fromEither[F](Uri.fromString(config.userInfoUri).leftMap(_.toString))
          respUser <- EitherT.liftF[F, String, JsonObject](clientResource.use { client =>
                       client.expect[JsonObject](
                         Request[F](
                           method = GET,
                           uri = uriUser,
                           headers = Headers(
                             Accept(MediaType.application.json),
                             Authorization(Credentials.Token(AuthScheme.Bearer, access_token))
                           )
                         )
                       )
                     })
          optUser = oauthUserManager
            .userFromOAuth(providerId, respUser)
            .map(v => Either.cond(v.isDefined, v.get, "error"))

          user <- EitherT(optUser)

        } yield user
        user.value
      }

    }
}
