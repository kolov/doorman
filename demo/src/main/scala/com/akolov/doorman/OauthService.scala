package com.akolov.doorman

import cats._
import cats.implicits._
import cats.effect._
import com.akolov.doorman.core._
import org.http4s.{HttpRoutes, StaticFile, Uri}
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Location

class OauthService[F[_]: Effect: Sync: ContextShift, User](
  config: DoormanConfig,
  clientResource: Resource[F, Client[F]],
  val doormanClient: Doorman[F, User],
  sessionManager: SessionManager[F, User])
    extends Http4sDsl[F] {

  object CodeMatcher extends QueryParamDecoderMatcher[String]("code")

  val oauth = new OauthEndpoints[F, User](clientResource, doormanClient, config)

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root / "login" / configname =>
      oauth.login(configname)

    case GET -> Root / "oauth" / "login" / configname :? CodeMatcher(code) =>
      val user: F[Either[String, User]] = oauth.callback(configname, code)

      user.flatMap {
        case Left(error) => Ok(s"Error during OAuth: $error")
        case Right(user) =>
          println(s"Got user: $user")
          TemporaryRedirect(Location(Uri.uri("/index.html"))).map(r => sessionManager.addUserCookie(user, r))
      }

  }

}
