package com.akolov.doorman.core

import cats._
import cats.data._
import cats.effect._
import io.circe._
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Accept, Authorization, Location, `Cache-Control`}
import org.http4s.{AuthScheme, Credentials, EntityDecoder, Headers, MediaType, Query, Request, Response, Uri}


case class OauthConfig(userAuthorizationUri: String,
                       accessTokenUri: String,
                       userInfoUri: String,
                       clientId: String,
                       clientSecret: String,
                       scope: Iterable[String],
                       redirectUrl: String
                      )

object OauthEndpoints {

  def apply[F[_]: Effect: Monad, User](
  clientResource: Resource[F, Client[F]],
  oauthUserManager: OAuthUserManager[F, User]
  ) =
new OauthEndpoints[F, User] with Http4sDsl[F] {

  implicit val jsonObjectDecoder: EntityDecoder[F, JsonObject] =
    jsonOf[F, JsonObject]

  def login(configname: String): F[Response[F]] = {
    val response = for {
      config <- config.provider(configname).toRight(ConfigurationNotFound(configname))
      uri <- LoginLogic.login(config)
    } yield MovedPermanently(
      location = Location(uri),
      body = s"""<head>
                |  <meta http-equiv="refresh" content="0; URL=$uri" />
                |</head>""".stripMargin,
      headers = `Cache-Control`(NonEmptyList(`no-cache`(), Nil))
    ).map(
      _.withContentType(
        `Content-Type`(MediaType.forExtension("html").get)
      )
    )

    response
      .toOption
      .getOrElse(
        BadRequest(s"Bad or missing configuration for $configname")
      )

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
          base <- EitherT.fromEither[F](Uri.fromString(config.accessTokenUri).leftMap(_.toString))
      uri = Uri(
        base.scheme,
        base.authority,
        base.path

      )

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
          access_token <- EitherT.fromOption[F](resp
                       .toMap
                       .get("access_token")
                       .flatMap(_.asString)
                       ,
                           "no access_token"
                         )
          uriUser <- EitherT.fromEither[F](Uri.fromString(config.userInfoUri).leftMap(_.toString))
          respUser <- EitherT.liftF[F, String, JsonObject](clientResource.use { client =>
                   client.expect[JsonObject](
                     Request[F](
                       method = GET,
                       uri = uriUser,
          headers = Headers(Accept(MediaType.application.json),
            Authorization(Credentials.Token(AuthScheme.Bearer, access_token)))
        ))
                 })
          optUser = oauthUserManager
            .userFromOAuth(providerId, respUser)
            .map(v => Either.cond(v.isDefined, v.get, "error"))

          user <- EitherT(optUser)

        } yield user
        user.value
      }

}
