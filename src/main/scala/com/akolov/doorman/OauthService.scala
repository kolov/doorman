package com.akolov.doorman

import cats.effect.{Effect, IO, Resource}
import com.google.api.client.auth.oauth2.{AuthorizationCodeTokenRequest, AuthorizationRequestUrl, BearerToken, Credential}
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.http.{BasicAuthentication, GenericUrl, HttpHeaders}
import com.google.api.client.json.jackson.JacksonFactory
import com.google.api.client.json.{GenericJson, JsonObjectParser}
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl

import scala.collection.JavaConverters._

case class OauthConfig(userAuthorizationUri: String,
                       accessTokenUri: String,
                       userInfoUri: String,
                       clientId: String,
                       clientSecret: String,
                       scope: Iterable[String],
                       redirectUrl: String
                      )

class OauthService[F[_] : Effect](configs: Map[String, OauthConfig], client: Resource[F, Client[F]]) extends Http4sDsl[F] {

  object CodeMatcher extends QueryParamDecoderMatcher[String]("code")

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "login" / configname =>
      configs.get(configname).map { config =>
        val url = new AuthorizationRequestUrl(config.userAuthorizationUri, config.clientId,
          List("code").asJava)
          .setScopes(config.scope.toList.asJava)
          .setRedirectUri(config.redirectUrl).build
        TemporaryRedirect(url)
      }.getOrElse(BadRequest(s"No configuration for oauth $configname"))
    case GET -> Root / "oauth" / "login" / configname :? CodeMatcher(code) =>
      configs.get(configname).map { config =>
        val response = new AuthorizationCodeTokenRequest(new NetHttpTransport, new JacksonFactory,
          new GenericUrl(config.accessTokenUri), code)
          .setRedirectUri(config.redirectUrl)
          .setClientAuthentication(
            new BasicAuthentication(config.clientId, config.clientSecret))
          .execute()

        val credential = new Credential(BearerToken.authorizationHeaderAccessMethod())
        credential.setFromTokenResponse(response)
        val userInfo = getUserInfo(credential.getAccessToken, config.userInfoUri)
        TemporaryRedirect("/")
      }.getOrElse(BadRequest(s"No configuration for oauth $configname"))
  }

  def getUserInfo(accessToken: String, userInfoUri: String): GenericJson = {
    val jsonFactory = new JacksonFactory()
    val requestFactory = new NetHttpTransport().createRequestFactory()
    val request = requestFactory.buildGetRequest(new GenericUrl(userInfoUri))
    request.setParser(new JsonObjectParser(jsonFactory))
    request.setThrowExceptionOnExecuteError(false)
    val headers = new HttpHeaders()
    headers.setAuthorization("Bearer " + accessToken)
    request.setHeaders(headers)
    val userInfoResponse = request.execute()
    if (userInfoResponse.isSuccessStatusCode()) {
      return userInfoResponse.parseAs(classOf[GenericJson])
    } else {
      return null
    }
  }

}
