package com.akolov.doorman.core

import io.circe.JsonObject

case class OAuthProviderConfig(
  userAuthorizationUri: String,
  accessTokenUri: String,
  userInfoUri: String,
  clientId: String,
  clientSecret: String,
  scope: Iterable[String],
  redirectUrl: String
)

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

trait OAuthUserManager[F[_], User] {

  /** Create User from (Oauth) user attributes.  */
  def userFromOAuth(providerId: String, json: JsonObject): F[Option[User]]

}
