package com.akolov.doorman.core

import cats._
import cats.implicits._
import cats.data._
import cats.effect._
import io.circe._
import org.http4s.CacheDirective.`no-cache`
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{`Cache-Control`, Accept, Authorization, Location}
import org.http4s.{AuthScheme, Credentials, EntityDecoder, Headers, MediaType, Query, Request, Response, Uri}

/**
  * This class provides the necessary endpoints to handle Oauth login. They need to be mapped to
  * routes. See the demo application for anexample
  */
class OauthEndpoints[F[_]: Effect: Monad, User](clientResource: Resource[F, Client[F]],
                                                doorman: UsersManager[F, User],
                                                providersLookup: ProvidersLookup)
    extends Http4sDsl[F] {

  implicit val jsonObjectDecoder: EntityDecoder[F, JsonObject] =
    jsonOf[F, JsonObject]

  def login(providerId: String): F[Response[F]] = {
    val uri: Option[Uri] = for {
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

    val responseMoved: Option[F[Response[F]]] = uri.map(
      u =>
        MovedPermanently(location = Location(u), body = "", headers = `Cache-Control`(NonEmptyList(`no-cache`(), Nil)))
    )

    responseMoved.getOrElse(BadRequest(s"Bad or missing configuration for $providerId"))

  }

  type ErrorOr[A] = EitherT[F, String, A]

  implicit class optionToErrorOr[A](o: Option[A]) {
    def toErrorOr(ifNone: String) = EitherT.fromOption[F](o, ifNone)
  }

  implicit class eitherToErrorOr[E, A](e: Either[E, A]) {
    def toErrorOr() = EitherT.fromEither[F](e.leftMap(_.toString))
  }

  def callback(configname: String, code: String): F[Either[String, User]] = {

    val user = for {
      config <- providersLookup.forId(configname).toErrorOr("No config")
      base <- Uri.fromString(config.accessTokenUri).toErrorOr
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
      access_token <- resp.toMap
                       .get("access_token")
                       .flatMap(_.asString)
                       .toErrorOr("no access_token")
      uriUser <- Uri.fromString(config.userInfoUri).toErrorOr
      respUser <- EitherT.liftF[F, String, JsonObject](clientResource.use { client =>
                   client.expect[JsonObject](
                     Request[F](method = GET,
                                uri = uriUser,
                                headers = Headers(Accept(MediaType.application.json),
                                                  Authorization(Credentials.Token(AuthScheme.Bearer, access_token))))
                   )
                 })
      optUser = doorman
        .userFromOAuth(configname, respUser.toMap.mapValues(_.toString))
        .map(v => Either.cond(v.isDefined, v.get, "err"))

      user <- EitherT(optUser)

    } yield user
    user.value
  }

}
