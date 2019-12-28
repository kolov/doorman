# Doorman

Oauth2 authentication and user session middleware for `http4s`.

User authentication and user tracking are two orthogonal concerns that often appear
close to each other and need to be hanndled together. This tiny library offers help with both.

# Usage

Read how to use `doorman` or jump right to the [demo](#demo)

Add dependency ```"com.akolov" %% "doorman" % "0.0.1"```.

### User tracking

Your web site may want to offer services to not authenticated users. A user may visit your site
a few times and find the resources he left by his last visit. 
If the user decides to authenticate at some stage, 
keeps his identity, enriching it with some attributes like name, email etc. 

`Doorman` offers two different middlewares to track users:
  - `authUserMiddleware` provides `AuthedRequest`, giving the application access to the user identity
  - `userTrackingMiddleware` is a weaker version - the user is tracked with a cookie,
   but the endpoint that does not need a user information gets a `Request`, not an `AuthedRequest`.

To use the any middleware, provide a `UserManager`:

```scala
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
```

Nothing spectacular about he middleware:

```
val routes = sessionManager.userTrackingMiddleware(
       HttpRoutes.of[F] {. . .}
     ) <+>
       sessionManager.authUserMiddleware(
         AuthedRoutes.of {. . . }
       )
```   
   
### Oauth2
   
For every OAuth2 provider a configuration is needed:

```case class OAuthProviderConfig(
     userAuthorizationUri: String,
     accessTokenUri: String,
     userInfoUri: String,
     clientId: String,
     clientSecret: String,
     scope: Iterable[String],
     redirectUrl: String
   )```

`ProvidersLookup`
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
  github {... }
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


# Demo

See the demo using everything mentioned above:

`sbt demo/run`
