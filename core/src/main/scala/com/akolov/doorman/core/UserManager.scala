package com.akolov.doorman.core

trait UserManager[F[_], User] {

  /** name of the tracking cookie */
  def cookieName: String

  /** Create a new non-authenticated user */
  def create: F[User]

  /** Marshall User to a cookie */
  def userToCookie(user: User): String

  /** Unmarshall cookie to User */
  def cookieToUser(cookie: String): F[Option[User]]

}
