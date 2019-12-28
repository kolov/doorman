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

trait UsersManager[F[_], User] {

  /** Create User from Oauth user data */
  def userFromOAuth(provider: String, data: Map[String, String]): F[Option[User]]

  /** Create a non-authenticated user */
  def create: F[User]

  /** Marshall User to a cookie */
  def userToCookie(user: User): String

  /** Unmarshall cookie to User */
  def cookieToUser(cookie: String): F[Option[User]]

  def cookieName: String

}
