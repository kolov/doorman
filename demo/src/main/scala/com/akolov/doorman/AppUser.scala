package com.akolov.doorman

import java.util.UUID

import cats.Monad
import cats.effect.IO
import com.akolov.doorman.core.Doorman
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import scala.util.Try

case class AppUser(uuid: String, name: Option[String] = None, data: Map[String, String] = Map())


trait JwtIssuer {

  private val name = "MyApp"

  private val algorithm = Algorithm.HMAC256("somesecret")

  private[this] val verifier = JWT.require(algorithm)
    .withIssuer(name)
    .build()

  def toCookie(user: AppUser): String = {
    val builder = JWT.create()
      .withIssuer(name)
      .withClaim("sub", user.uuid)

    val builder1 = user.name
      .map(name => builder.withClaim("name", name))
      .getOrElse(builder)

    builder1.sign(algorithm)
  }

  def parseCookie(token: String): Option[AppUser] =
    Try(verifier.verify(token)).toOption.map { payload =>
      AppUser(payload.getSubject, Option(payload.getClaim("name").asString))
    }

}

object SimpleDoorman extends Doorman[IO, AppUser] with JwtIssuer {

  override def fromProvider(provider: String, data: Map[String, String]): IO[Option[AppUser]] = {
    IO.delay(provider match {
      case "google" => Some(AppUser(UUID.randomUUID.toString, data.get("name"), data))
      case "github" => Some(AppUser(UUID.randomUUID.toString, data.get("name"), data))
    })
  }

  override def create()(implicit ev: Monad[IO]): IO[AppUser] = IO.delay(AppUser(UUID.randomUUID.toString))

  /**
    * Unmarshall cookie to User
    */
  override def toUser(cookie: String): IO[Option[AppUser]] = IO.delay(parseCookie(cookie))
}

