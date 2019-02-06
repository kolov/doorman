package com.akolov.doorman

import java.util.UUID

import cats.Monad
import cats.effect.{IO, Sync}
import com.akolov.doorman.core.{Doorman, DoormanConfig}
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import scala.util.Try

case class OauthIdentity(provider: String, sub: String)

case class AppUser(uuid: String, identity: Option[OauthIdentity] = None, name: Option[String] = None)


object SimpleDoorman extends Doorman[IO, AppUser] {
  //  override type User = AppUser

  private val name = "MyApp"

  private val algorithm = Algorithm.HMAC256("dsdfdsfdsfdsf") // TODO from config

  private[this] val verifier = JWT.require(algorithm)
    .withIssuer(name)
    .build()

  override def fromProvider(provider: String, data: Map[String, String]): IO[AppUser] = {
    IO.delay(AppUser(UUID.randomUUID.toString, data.get("sub").map(OauthIdentity(provider, _)), data.get("firstName")))
  }

  override def create()(implicit ev: Monad[IO]): IO[AppUser] = IO.delay(AppUser(UUID.randomUUID.toString))

  override def toCookie(user: AppUser): String = {
    val builder = JWT.create()
      .withIssuer(name)
      .withClaim("sub", user.uuid)

    val builder0 = user.identity
      .map(p => builder.withClaim("provider", p.provider).withClaim("providerId", p.sub))
      .getOrElse(builder)

    val builder1 = user.name
      .map(name => builder.withClaim("given_name", name))
      .getOrElse(builder0)

    builder1.sign(algorithm)
  }

  def toUser(token: String): Option[AppUser] =
    Try(verifier.verify(token)).toOption.map { payload =>
      val identity = for {
        provider <- Option(payload.getClaim("provider").asString)
        providerId <- Option(payload.getClaim("providerId").asString)
      } yield ( OauthIdentity(provider, providerId) )

      AppUser(payload.getSubject, identity, Option(payload.getClaim("name").asString))
    }

  override def config: DoormanConfig = DoormanConfig(
    cookieName = "demo-auth"
  )
}

