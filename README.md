# Doorman

Oauth2 authentication and user tracking middleware for `http4s`.

User authentication and user tracking are two orthogonal concerns that often
need to be handled together. This tiny library offers help with both.

# Usage

Read how to use `doorman` or jump right to the [demo](#demo)

Add dependency ```"com.akolov" %% "doorman" % "0.3.2"```.

### User tracking

Your web site may want to offer services to not authenticated users. A user may visit the site
a few times and find the resources he left by his last visit. 
If the user decides to authenticate at some stage, he
keeps his identity, enriching it with some attributes like name, email etc. 

`Doorman` offers `AuthMiddleware` to track users. It builds
`AuthedRequest`, giving the application access to the user identity. Non-logged users 
have identities too.


Note: There is also a weaker version of the middleware: `userTrackingMiddleware`. 
The user is tracked with a cookie,
but the endpoint that does not need a user information gets a `Request`, 
not an `AuthedRequest`. It is useful when tools outside of the application need user tracking cookie.
Forget about if yo don't need that.

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

Nothing spectacular about its usage:


```scala
class DemoService[F[_]: Effect: ContextShift](userManager: UserManager[F, AppUser])
  extends Http4sDsl[F] {

    val auth = DoormanAuthMiddleware(userManager)
    val routes = auth(
      AuthedRoutes.of[AppUser, F] {
        case GET -> Root / "userinfo"  as user =>
         Ok(s"Hello, $user")
      }
    )
}
```   

When the endpoint is hit, the request will be analyzed by the `UserManager` 
and the endpoint function will get either the user from the cookie, 
if one exists,
or a newly created user. 
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
     scope: Iterable[String],
     redirectUrl: String
   )
```

Given a configuration, `OauthEndpoints` helps handle user login and callback:

```scala
trait OauthEndpoints[F[_], User] {
  // Builds a url to redirect the user to for authentication
  def login(config: OAuthProviderConfig): Option[Uri]
  // handles the OAuth2 callback
  def callback(providerId: String, config: OAuthProviderConfig, code: String): F[Either[String, User]]
}
```
The application needs to expose endpoints providing redirect to the login UR 
and processing of the callback.
See the demo application for an example how to tie all together.


# Demo

A simple application with user tracking and OAuth2. 

Start a fake OAuth server with:

`docker run -p 8282:8282 --name fakeoauth -e PERMITTED_REDIRECT_URLS=http://localhost:8080/oauth/login/fake  pkbdev/fake-oauth2-server`

To run the demo: `sbt demo/run` and point your browser to `http://localhost:8080`.

The demo works with the fake provider running at `localhost:8282`. 

It has been tested with Google too, provided 
 the environment variables `OAUTH2_GOOGLE_CLIENT_ID`, `OAUTH2_GOOGLE_CLIENT_SECRET` and
  `OAUTH2_GOOGLE_REDIRECT_URL` has to be set to valid values (see `application.conf`)


## Developmnet

`sbt '+ publishSigned'`
`sbt sonatypeReleaseAll`
 


 


