# Doorman

Http4s middleware for Oauth2 authentication and session management.

This project has not reached releasable state, do not use yet!

# Usage

Configure a `DoormanClient`:

```trait DoormanClient[F[_], User] {
     //  type User
   
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
   
   
Then in the service:

    val sessionManager = SessionManager(doormanClient)
    val routes: HttpRoutes[F] = sessionManager.middleware(
        AuthedService {
          case GET -> Root / "hello"  as user =>
            Ok(Json.obj("message" -> Json.fromString(s"Hello, ${user}")))
        }
      )