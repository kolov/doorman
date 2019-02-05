package com.akolov.doorman

import cats._
import cats.effect._
import com.akolov.doorman.core.{DoormanClient, OauthConfig, OauthMethods, SessionManager}
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.dsl.Http4sDsl


class OauthService[F[_] : Effect : Monad, User](configs: Map[String, OauthConfig],
                                                clientResource: Resource[F, Client[F]],
                                                val doormanClient: DoormanClient[F, User],
                                                sessionManager: SessionManager[F, User]
                                               ) extends Http4sDsl[F] {

  object CodeMatcher extends QueryParamDecoderMatcher[String]("code")

  val methods = new OauthMethods[F, User](configs, clientResource, sessionManager)

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "login" / configname =>
      methods.login(configname)

    case GET -> Root / "oauth" / "login" / configname :? CodeMatcher(code) =>
      methods.callback(configname, code)

  }


}