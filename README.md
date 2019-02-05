# Doorman

Http4s middleware for Oauth2 authentication and session management.

This project has not reached releasable state, do not use yet!

# Usage

Configure a `DoormanClient`:

    trait DoormanClient[F[_], User] {
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
   
    }
   
   
Provide user information in the service:

    val sessionManager = SessionManager(doormanClient)
    val routes: HttpRoutes[F] = sessionManager.middleware(
        AuthedService {
          case GET -> Root / "hello"  as user =>
            Ok(Json.obj("message" -> Json.fromString(s"Hello, ${user}")))
        }
      )
      
Add routes for Oauth2 login:

      def routes: HttpRoutes[F] = HttpRoutes.of[F] {
      
        val methods = new OauthMethods[F, User](config, clientResource, sessionManager)
        
        case GET -> Root / "login" / configname =>
          methods.login(configname)
    
        case GET -> Root / "oauth" / "login" / configname :? CodeMatcher(code) =>
          methods.callback(configname, code)
    
      }      
      