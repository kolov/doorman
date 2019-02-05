package com.akolov.doorman.core

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
