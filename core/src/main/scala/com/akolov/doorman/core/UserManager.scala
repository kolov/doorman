package com.akolov.doorman.core

case class OAuthProviderConfig(
  userAuthorizationUri: String,
  accessTokenUri: String,
  userInfoUri: String,
  clientId: String,
  clientSecret: String,
  scope: Iterable[String],
  redirectUrl: String
)

trait ProvidersLookup {

  def forId(provider: String): Option[OAuthProviderConfig]
}

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

trait OAuthUserManager[F[_], User] extends UserManager[F, User] {

  /** Create User from (Oauth) user attributes.  */
  def userFromOAuth(provider: String, data: Map[String, String]): F[Option[User]]

}
