package com.akolov.doorman

import java.util.UUID

import cats.Monad
import cats.effect.{IO, Sync}
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import scala.util.Try

trait DoormanClient[F[_], User] {
  //  type User

  /**
  Create User from Oauth user data
   */
  def fromProvider(data: Map[String, String]): F[User]

  /**
  Create a non-authenticated user
   */
  def create()(implicit ev: Monad[F]): F[User]

  /**
    * marshall th user to a cookie
    */
  def toCookie(user: User): String

  /**
    * Unmarshall cookie to User
    */
  def toUser(cookie: String): Option[User]

}

// Concrete

case class AppUser(uuid: String, identity: Option[String] = None)


object AppUser {

  def create[F[_] : Sync]: F[AppUser] = Sync[F].pure(new AppUser(UUID.randomUUID.toString))

  def forProvider(uuid: String, identity: Option[String]): AppUser = new AppUser(uuid, identity)
}

object SimpleDoormanClient extends DoormanClient[IO, AppUser] {
  //  override type User = AppUser

  private val name = "MyApp"

  private val algorithm = Algorithm.HMAC256("dsdfdsfdsfdsf") // TODO from config

  private[this] val verifier = JWT.require(algorithm)
    .withIssuer(name)
    .build()

  override def fromProvider(data: Map[String, String]): IO[AppUser] = {
    val sub = data.get("sub")
    IO.delay(AppUser(UUID.randomUUID.toString, sub))
  }

  override def create()(implicit ev: Monad[IO]): IO[AppUser] = IO.delay(AppUser(UUID.randomUUID.toString, None))

  override def toCookie(user: AppUser): String = {
    val builder = JWT.create()
      .withIssuer(name)
      .withClaim("sub", user.uuid)

    user.identity
      .map(p => builder.withClaim("provider", p))
      .getOrElse(builder)
      .sign(algorithm)
  }

  def toUser(token: String): Option[AppUser] =
    Try(verifier.verify(token)).toOption.map { payload =>
      AppUser(payload.getSubject, Option(payload.getClaim("provider").asString))
    }

}

