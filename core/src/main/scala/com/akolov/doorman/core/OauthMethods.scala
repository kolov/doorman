package com.akolov.doorman.core

import cats._
import cats.data._
import cats.effect._
import cats.implicits._
import com.google.api.client.auth.oauth2._
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.{BasicAuthentication, GenericUrl}
import com.google.api.client.json.jackson.JacksonFactory
import io.circe.Decoder.Result
import io.circe._
import org.http4s.CacheDirective.`no-cache`
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.{Accept, Authorization, Location, `Cache-Control`}
import org.http4s.{AuthScheme, Credentials, EntityDecoder, Headers, MediaType, Request, Response, Uri}

import scala.collection.JavaConverters._


case class OauthConfig(userAuthorizationUri: String,
                       accessTokenUri: String,
                       userInfoUri: String,
                       clientId: String,
                       clientSecret: String,
                       scope: Iterable[String],
                       redirectUrl: String
                      )

class OauthMethods[F[_] : Effect : Monad, User](configs: Map[String, OauthConfig],
                                                clientResource: Resource[F, Client[F]],
                                                sessionManager: SessionManager[F, User]
                                               ) extends Http4sDsl[F] {


  def login(configname: String) = {
    val redirectUri: F[Option[Uri]] = (for {
      config <- OptionT.fromOption[F](configs.get(configname))
      url = new AuthorizationRequestUrl(config.userAuthorizationUri, config.clientId,
        List("code").asJava)
        .setScopes(config.scope.toList.asJava)
        .setRedirectUri(config.redirectUrl).build
      option <- OptionT.fromOption[F](Uri.fromString(url).toOption)
    } yield option).value

    val responseMoved: F[Option[F[Response[F]]]] = redirectUri.map { (ou: Option[Uri]) =>
      ou.map { uri =>
        MovedPermanently(
          location = Location(uri),
          body = "",
          headers = `Cache-Control`(NonEmptyList(`no-cache`(), Nil)))
      }
    }
    responseMoved.flatMap(_.getOrElse(BadRequest(s"No configuration for oauth $configname")))
  }

  def callback(configname: String, code: String) = {
    val userInfo: F[Option[JsonObject]] = (for {
      config <- OptionT.fromOption[F](configs.get(configname))
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
        val resp: F[Response[F]] = Ok("found user")
        resp.flatMap { (r: Response[F]) =>
          val fu: F[User] = sessionManager.doormanClient.fromProvider(dataMap)
          val xxx: F[Response[F]] = fu.flatMap(u => sessionManager.userRegistered(u, r))
          xxx
        }
      }
      resp.getOrElse(BadRequest("xx"))

    }
  }

  // returns F[Response[F]]

  def getUserInfo(accessToken: String, userInfoUri: String): F[Option[JsonObject]] = {

    val request: Request[F] = Request[F](method = GET,
      uri = Uri.unsafeFromString(userInfoUri),
      headers = Headers(
        Authorization(Credentials.Token(AuthScheme.Bearer, accessToken)),
        Accept(MediaType.application.json)))

    val decoderJson = implicitly[Decoder[Json]]

    implicit val decoderJsonObject = new Decoder[JsonObject] {
      override def apply(c: HCursor): Result[JsonObject] = {
        decoderJson.apply(c).flatMap { j: Json =>
          j.asObject match {
            case Some(o) => Right(o)
            case None => Left(DecodingFailure("Not an object", List()))
          }
        }
      }
    }

    implicit val jof: EntityDecoder[F, JsonObject] = jsonOf[F, JsonObject]

    clientResource.use { (client: Client[F]) =>
      val r: F[JsonObject] = client.expect[JsonObject](request)
      println(s"r = $r")
      r.map { user => println(s"user=$user"); Some(user) }
    }

  }


}
