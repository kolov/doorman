# Doorman

Http4s middleware for Oauth2 authentication and session management.

This project has not reached releasable state, do not use yet!

# Usage

Configure a `Doorman`:
    ```scala
    trait Doorman[F[_], User] {
         /**
         Create User from Oauth user data
          */
         def fromProvider(data: Map[String, String]): F[User]
       
         /**
         Create a non-authenticated user
          */
         def create()(implicit ev: Monad[F]): F[User]
       
         /**
           * marshall th user to a cookie
           */
         def toCookie(user: User): String
       
         /**
           * Unmarshall cookie to User
           */
         def toUser(cookie: String): Option[User]
    }```
   
   
Configure outh providers:
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
````
   
   
Provide user information in the service:
    ```scala
    val sessionManager = SessionManager(doormanClient)
    val routes: HttpRoutes[F] = sessionManager.middleware(
        AuthedService {
          case GET -> Root / "hello"  as user =>
            Ok(Json.obj("message" -> Json.fromString(s"Hello, ${user}")))
        }
      )```
      
      
Add routes for Oauth2 login:

    ```scala
      def routes: HttpRoutes[F] = HttpRoutes.of[F] {
      
        val oauth = new OauthMethods[F, User](config, clientResource, sessionManager)
        
        case GET -> Root / "login" / configname =>
          oauth.login(configname)
    
        case GET -> Root / "oauth" / "login" / configname :? CodeMatcher(code) =>
          oauth.callback(configname, code)
    
      }      ```
      
