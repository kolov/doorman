package com.akolov.doorman.core

import cats.Monad

trait Doorman[F[_], User] {
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
    * marshall User to a cookie
    */
  def toCookie(user: User): String

  /**
    * Unmarshall cookie to User
    */
  def toUser(cookie: String): Option[User]

}
