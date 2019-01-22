package com.akolov.doorman

import java.util.UUID

import cats.implicits._
import cats.effect.Sync
import org.http4s.HttpApp
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.middleware.{CORS, CORSConfig}

sealed trait UserIdentity

case class OauthUser(id: String) extends UserIdentity

case class AppUser(uuid: String, userIdentity: Option[UserIdentity] = None)

object AppUser {

  def create[F[_] : Sync]: F[AppUser] = Sync[F].pure(new AppUser(UUID.randomUUID.toString))

  def forProvider(uuid: String, provider: Option[String]): AppUser = new AppUser(uuid, provider.map(OauthUser(_)))
}

class AllServices[F[_] : Sync, User](oauthService: OauthService[F], helloWorldService: HelloWorldService[F, User], corsConfig: CORSConfig) {

  def app: HttpApp[F] = {
    Router(
      "/api/v1" -> CORS(helloWorldService.routes <+> oauthService.routes, corsConfig),
    ).orNotFound
  }
}
