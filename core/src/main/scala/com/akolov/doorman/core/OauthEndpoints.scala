package com.akolov.doorman.core

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import io.circe._
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Accept, Authorization}
import org.http4s.{AuthScheme, Credentials, EntityDecoder, Headers, MediaType, Query, Request, Uri}

/**
  * This class provides the necessary endpoints to handle Oauth login. They need to be mapped to
  * routes. See the demo application for anexample
  */
class OauthEndpoints[F[_]: Effect: Monad, User](
  clientResource: Resource[F, Client[F]],
  doorman: OAuthUserManager[F, User],
  providersLookup: ProvidersLookup
) extends Http4sDsl[F] {

  implicit val jsonObjectDecoder: EntityDecoder[F, JsonObject] =
    jsonOf[F, JsonObject]

  def login(providerId: String): Option[Uri] =
    for {
      config <- providersLookup.forId(providerId)
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

  def callback(providerId: String, code: String): F[Either[String, User]] = {
    val user = for {
      config <- EitherT.fromOption[F](providersLookup.forId(providerId), s"Unknown provider: $providerId")
      base <- EitherT.fromEither[F](Uri.fromString(config.accessTokenUri).leftMap(_.toString))
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

      request = Request[F](method = POST, uri = uri, headers = Headers(Accept(MediaType.application.json)))
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
      optUser = doorman
        .userFromOAuth(providerId, respUser.toMap.mapValues(_.toString))
        .map(v => Either.cond(v.isDefined, v.get, "err"))

      user <- EitherT(optUser)

    } yield user
    user.value
  }

}
