# Doorman

Oauth2 authentication and user session middleware for `http4s`.

The project is in early alpha, use with care!

# Usage

Read how to use `doorman` or jump right to the [demo](#demo)
Add dependency ```  "com.akolov" %% "doorman" % "0.0.1"```.

Configure a `Doorman`:

```scala
trait Doorman[F[_], User] {

  /** Create User from Oauth user data */
  def fromProvider(provider: String, data: Map[String, String]): F[Option[User]]

  /** Create a non-authenticated user */
  def create()(implicit ev: Monad[F]): F[User]

  /** Marshall User to a cookie */
  def toCookie(user: User): String

  /** Unmarshall cookie to User */
  def toUser(cookie: String): F[Option[User]]

}
```
   
   
Configure oauth providers:

```yaml
 oauth {
  google {
    clientId: "set in env var"
    clientId: ${?OAUTH2_GOOGLE_CLIENT_ID}
    clientSecret: "set in env var"
    clientSecret: ${?OAUTH2_GOOGLE_CLIENT_SECRET}
    userAuthorizationUri: "https://accounts.google.com/o/oauth2/v2/auth"
    accessTokenUri: "https://www.googleapis.com/oauth2/v4/token"
    clientAuthenticationScheme: "form"
    scope: ["openid", "email", "profile"]
    userInfoUri: "https://www.googleapis.com/oauth2/v3/userinfo"
    redirectUrl: "http://localhost:8080/api/v1/oauth/login/google"
    redirectUrl: ${?OAUTH2_GOOGLE_REDIRECT_URL}
  }
  github {
    clientId: "set in env var"
    clientId: ${?OAUTH2_GITHUB_CLIENT_ID}
    clientSecret: "set in env var"
    clientSecret: ${?OAUTH2_GITHUB_CLIENT_SECRET}
    userAuthorizationUri: "https://github.com/login/oauth/authorize"
    accessTokenUri: "https://github.com/login/oauth/access_token"
    clientAuthenticationScheme: "form"
    scope: ["openid", "email", profile]
    userInfoUri: "https://api.github.com/user"
    redirectUrl: "http://localhost:8080/api/v1/oauth/login/github"
    redirectUrl: ${?OAUTH2_GITHUB_REDIRECT_URL}
  }
```
   

To allow users to login, add routes for Oauth2 login. Decide how to handle the login outcome yourself:

```scala
val oauth = new OauthEndpoints[F, User](clientResource, doormanClient, config)

  def routes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root / "login" / configname =>
      oauth.login(configname)

    case GET -> Root / "oauth" / "login" / configname :? CodeMatcher(code) =>
      val user: F[Either[String, User]] = oauth.callback(configname, code)
      user.flatMap {
        case Left(error) => Ok(s"Error: $error")
        case Right(user) => Ok(s"User: $user").map(r => sessionManager.addUserCookie(user, r))
      }
  }
```

## Session Management

To access user information in the service:

```scala
    val sessionManager = SessionManager(doormanClient)
    val routes: HttpRoutes[F] = sessionManager.middleware(
        AuthedService {
          case GET -> Root / "hello"  as user =>
            Ok(Json.obj("message" -> Json.fromString(s"Hello, ${user}")))
        }
      )
```
Note that both authenticated and not-authenticated users are tracked with a cookie.

# Demo

`sbt demo/run`
