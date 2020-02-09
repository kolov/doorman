# Doorman

Oauth2 authentication and user tracking middleware for `http4s`.

User authentication and user tracking are two orthogonal concerns that yet often
need to be handled together. This tiny library offers help with both.

# Usage

Read how to use `doorman` or jump right to the [demo](#demo)

Add dependency `"com.akolov" %% "doorman" % "0.3.3"`.

### User tracking

Your web site may want to offer services to not authenticated users. As a user
returns to the site, he will find the resources as he left them at his previous visit. 
If the user decides to authenticate at some stage, he
keeps his identity, enriching it with some attributes like name, email etc. 

`Doorman` offers `AuthMiddleware` to track users with a cookie. It builds
`AuthedRequest`, giving the application access to the user identity. Non-logged users 
have identities too.


There is also a weaker version of the middleware: `UserTrackingMiddleware`. 
The user is tracked with a cookie,
but the endpoint that does not need a user information gets a `Request`, 
not an `AuthedRequest`. It is useful when tools outside of the application need user tracking cookie.
Forget about if yo don't need that.

To use the any middleware, provide a `UserManager`:


```scala
val myUserManager = new UserManager[F, AppUser] {

  /** Create a new non-authenticated user */
  override def create: F[AppUser] = ???

  /** Marshall User to a cookie */
  override def userToCookie(user: AppUser): String = ???

  /** Unmarshall cookie to User */
  override def cookieToUser(cookie: String): F[Option[AppUser]] = ???

}
// myUserManager: AnyRef with UserManager[F, AppUser] = repl.Session$App$$anon$1@3ca9c607
```

Given a `UserManager`, Doorman provides `DoormanAuthMiddleware` and
`DoormanTrackingMiddleware`: 

```scala
class DemoService[F[_]: Effect: ContextShift](userManager: UserManager[F, AppUser])
  extends Http4sDsl[F] {

    val cookieConfig = CookieConfig(name = "demo-user", path = Some("/"))
    val auth = DoormanAuthMiddleware(userManager, cookieConfig)
    val track = DoormanTrackingMiddleware(userManager, cookieConfig)
    val routes = auth(
      AuthedRoutes.of[AppUser, F] {
        case GET -> Root / "userinfo"  as user =>
         Ok(s"Hello, $user")
      }
    )
}

val service = new DemoService(myUserManager)
// service: DemoService[F] = repl.Session$App$DemoService@314228e0
```   

When the endpoint is hit, the request will be analyzed by the `UserManager` 
and the endpoint function will receive either the user from the cookie, 
if one exists, or a newly created user. 
In the case of a new user, a cookie will be set in the response. 

### Oauth2
   
`Doorman` needs a configuration for every OAuth2 provider:

```scala  mdoc
case class OAuthProviderConfig(
     userAuthorizationUri: String,
     accessTokenUri: String,
     userInfoUri: String,
     clientId: String,
     clientSecret: String,
     scope: List[String],
     redirectUrl: String
   )
```

Given a configuration, `OauthEndpoints` provides handlers for the OAuth 
 endpoints: login and callback.
`login` constructs a login URL based on the configuration. It is up to
the application set up a login endpoint that redirects the user  to this URL.
`callback` handles th OAuth callback after successful authentication. It first retrieves an
access token, than user details. It is up to the application to handle the OAuth2 user data.

```scala
trait OauthEndpoints[F[_]] {

  // Builds a url to redirect the user to for authentication
  def login(config: OAuthProviderConfig): Option[Uri]

  // handles the OAuth2 callback
  def callback(config: OAuthProviderConfig,
                 code: String,
                 client: Client[F]): F[Either[DoormanError, UserData]]
}

val endpoints = OAuthEndpoints[IO]()
// endpoints: AnyRef with OAuthEndpoints[IO] with Http4sDsl[IO] with client.dsl.Http4sClientDsl[IO] = com.akolov.doorman.core.OAuthEndpoints$$anon$1@ee16b36
```

See the demo application for an example how to tie all together.

# Demo

A simple application with user tracking and OAuth2. User is tracked with a JWT cookie. It has been tested with
[fake-oauth2-server](https://github.com/patientsknowbest/fake-oauth2-server) and [Google OAuth2](https://developers.google.com/identity/protocols/OAuth2)

## fake-oauth2-server

Start a server with:

`docker run -p 8282:8282 --name fakeoauth -e PERMITTED_REDIRECT_URLS=http://localhost:8080/oauth/login/fake  pkbdev/fake-oauth2-server`

To run the demo: `sbt demo/run` and point your browser to `http://localhost:8080`.

The demo works with the fake provider running at `localhost:8282`. 

## Google OAuth2

To run the demo, you need to setup your OAuth2 with Google, then privide configuration viq
 the environment variables `OAUTH2_GOOGLE_CLIENT_ID`, `OAUTH2_GOOGLE_CLIENT_SECRET` and
  `OAUTH2_GOOGLE_REDIRECT_URL` (see `application.conf`)


## Developer's notes

    sbt '+ publishSigned'
    sbt sonatypeReleaseAll

    sbt '++2.12.10! docs/mdoc'
 


 


