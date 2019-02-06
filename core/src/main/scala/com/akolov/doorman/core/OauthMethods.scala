package com.akolov.doorman.core

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import com.google.api.client.auth.oauth2._
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.{BasicAuthentication, GenericUrl}
import com.google.api.client.json.jackson.JacksonFactory
import io.circe._
import org.http4s.CacheDirective.`no-cache`
import org.http4s.circe.jsonOf
import org.http4s.client.Client
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

/**
  * This class provides the necessary endpoints to handle Oauth login. They need to be mapped to
  * routes.
  */
class OauthMethods[F[_] : Effect : Monad, User](clientResource: Resource[F, Client[F]],
                                                sessionManager: SessionManager[F, User],
                                                config: DoormanConfig
                                               ) extends Http4sDsl[F] {

  implicit val jsonObjectDecoder: EntityDecoder[F, JsonObject] = jsonOf[F, JsonObject]

  def login(configname: String): F[Response[F]] = {
    val uri: Option[Uri] = for {
      config <- config.provider(configname)
      base <- Uri.fromString(config.userAuthorizationUri).toOption
      uri = Uri(base.scheme, base.authority, base.path,
        Query(("redirect_uri", Some(config.redirectUrl)),
          ("client_id", Some(config.clientId)),
          ("response_type", Some("code")),
          ("scope", Some(config.scope.mkString(" ")))),
        base.fragment)
    } yield uri

    val responseMoved: Option[F[Response[F]]] = uri.map(u => MovedPermanently(
      location = Location(u),
      body = "",
      headers = `Cache-Control`(NonEmptyList(`no-cache`(), Nil))))

    responseMoved.getOrElse(BadRequest(s"Bad or missing configuration for $configname"))

  }

  def callback(configname: String, code: String): F[Response[F]] = {
    val userInfo: F[Option[JsonObject]] = (for {
      config <- OptionT.fromOption[F](config.provider(configname))
      resp = new AuthorizationCodeTokenRequest(new NetHttpTransport, new JacksonFactory,
        new GenericUrl(config.accessTokenUri), code)
        .setRedirectUri(config.redirectUrl)
        .setClientAuthentication(
          new BasicAuthentication(config.clientId, config.clientSecret))
        .execute()

      credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
      _ = credential.setFromTokenResponse(resp)
      userInfo <- OptionT(getUserInfo(credential.getAccessToken, config.userInfoUri))

    } yield userInfo).value


    userInfo.flatMap { (ou: Option[JsonObject]) =>
      val resp: Option[F[Response[F]]] = ou.map { json =>

        val dataMap = json.toMap.mapValues(_.toString)
        val resp: F[Response[F]] = Ok(s"Constructing user from data $dataMap")
        resp.flatMap { (r: Response[F]) =>
          sessionManager.doorman.fromProvider(configname, dataMap).flatMap(u => sessionManager.userRegistered(u, r))
        }
      }
      resp.getOrElse(BadRequest(""))

    }
  }

  def getUserInfo(accessToken: String, userInfoUri: String): F[Option[JsonObject]] = {

    val request: Request[F] = Request[F](method = GET,
      uri = Uri.unsafeFromString(userInfoUri),
      headers = Headers(
        Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)),
        Accept(MediaType.application.json)))

    clientResource.use { (client: Client[F]) =>
      client.expect[JsonObject](request).map(Some(_))
    }

  }


}
