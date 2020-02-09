package com.akolov.doorman.demo

import java.util.UUID

import cats.effect.IO
import com.akolov.doorman.core.{UserData, UserManager}
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import scala.util.Try

case class AppUser(
  uuid: UUID,
  authenticated: Boolean = false,
  name: Option[String] = None,
  data: Map[String, String] = Map.empty
) {
  def identified(data: UserData) = AppUser(uuid, true, data.attrs.get("name"), data.attrs)
}

trait JwtIssuer[F[_]] {
  private val appName = "DemoApp"

  private val algorithm = Algorithm.HMAC256("somesecret")

  private[this] val verifier = JWT
    .require(algorithm)
    .withIssuer(appName)
    .build()

  def userToCookie(user: AppUser): String = {
    val builder = JWT
      .create()
      .withIssuer(appName)
      .withClaim("sub", user.uuid.toString)
      .withClaim("authenticated", user.authenticated)

    val builder1 = user.name
      .map(name => builder.withClaim("name", name))
      .getOrElse(builder)

    builder1.sign(algorithm)
  }

  def parseCookie(token: String): Option[AppUser] =
    Try(verifier.verify(token)).toOption.flatMap { payload =>
      Try(UUID.fromString(payload.getSubject)).toOption.map { uuid =>
        AppUser(
          uuid,
          payload.getClaim("authenticated").asBoolean,
          Option(payload.getClaim("name").asString)
        )
      }
    }
}

object DemoUserManager extends UserManager[IO, AppUser] with JwtIssuer[IO] {

  override def create(): IO[AppUser] =
    IO.delay(AppUser(UUID.randomUUID))

  override def cookieToUser(cookie: String): IO[Option[AppUser]] =
    IO.delay(parseCookie(cookie))
}
