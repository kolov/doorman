package com.akolov.doorman.core

import cats.Monad

case class DoormanConfig( cookieName: String)

trait Doorman[F[_], User] {

  def config: DoormanConfig
  /**
  Create User from Oauth user data
   */
  def fromProvider(provider: String, data: Map[String, String]): F[User]

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
