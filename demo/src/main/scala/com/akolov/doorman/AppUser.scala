package com.akolov.doorman

import java.util.UUID

import cats.effect.IO
import com.akolov.doorman.core.OAuthUserManager
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import scala.util.Try

case class AppUser(uuid: String, name: Option[String] = None, data: Map[String, String] = Map())

trait JwtIssuer[F[_]] {

  private val name = "MyApp"

  private val algorithm = Algorithm.HMAC256("somesecret")

  private[this] val verifier = JWT
    .require(algorithm)
    .withIssuer(name)
    .build()

  def userToCookie(user: AppUser): String = {
    val builder = JWT
      .create()
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

object DemoUserManager extends OAuthUserManager[IO, AppUser] with JwtIssuer[IO] {

  override def cookieName: String = "demo-app-user"

  override def create(): IO[AppUser] =
    IO.delay(AppUser(UUID.randomUUID.toString))

  override def cookieToUser(cookie: String): IO[Option[AppUser]] =
    IO.delay(parseCookie(cookie))

  override def userFromOAuth(provider: String, data: Map[String, String]): IO[Option[AppUser]] =
    for {
      id <- IO.delay(UUID.randomUUID.toString)
      user = provider match {
        case "google" =>
          Some(AppUser(id, data.get("name"), data))
        case "github" =>
          Some(AppUser(id, data.get("name"), data))
      }
    } yield user
}
