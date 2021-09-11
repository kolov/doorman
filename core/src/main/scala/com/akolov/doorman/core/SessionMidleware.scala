package com.akolov.doorman.core

import cats._
import cats.data._
import cats.implicits._
import org.http4s.headers.Cookie
import org.http4s.server.{AuthMiddleware, Middleware}
import org.http4s.{AuthedRequest, HttpDate, Request, Response, ResponseCookie}

case class CookieConfig(
  name: String,
  // TODO make expires Duration expires: Option[HttpDate] = None,
  maxAge: Option[Long] = None,
  domain: Option[String] = None,
  path: Option[String] = None,
  secure: Boolean = false,
  httpOnly: Boolean = false,
  extension: Option[String] = None)

object DoormanTrackingMiddleware extends DoormanMiddleware {
  type UserTrackingMiddleware[F[_], _] = Middleware[OptionT[F, *], Request[F], Response[F], Request[F], Response[F]]

  def apply[F[_] : Monad, User](
    userManager: UserManager[F, User],
    cookieConfig: CookieConfig): UserTrackingMiddleware[F, User] = {
    (service: Kleisli[OptionT[F, *], Request[F], Response[F]]) =>
      Kleisli { (r: Request[F]) =>
        OptionT(userFromRequest(r, userManager, cookieConfig.name).flatMap {
          case (isOld, user) =>
            val resp: OptionT[F, Response[F]] = service.run(r)
            if (isOld) {
              resp.value
            } else {
              val cookie = ResponseCookie(
                name = cookieConfig.name,
                userManager.userToCookie(user),
                expires = None,
                maxAge = cookieConfig.maxAge,
                domain = cookieConfig.domain,
                path = cookieConfig.path,
                secure = cookieConfig.secure,
                httpOnly = cookieConfig.httpOnly,
                extension = cookieConfig.extension
              )
              resp.map(_.addCookie(cookie)).value
            }
        })
      }
  }
}

object DoormanAuthMiddleware extends DoormanMiddleware {

  def apply[F[_]: Monad, User](
    userManager: UserManager[F, User],
    cookieConfig: CookieConfig): AuthMiddleware[F, User] = {
    (service: Kleisli[OptionT[F, *], AuthedRequest[F, User], Response[F]]) =>
      Kleisli { (r: Request[F]) =>
        val respf: F[Option[Response[F]]] = userFromRequest(r, userManager, cookieConfig.name).flatMap {
          case (isOld, user) =>
            val resp: OptionT[F, Response[F]] = service.run(AuthedRequest(user, r))
            if (isOld) {
              resp.value
            } else {
              val cookie = ResponseCookie(
                name = cookieConfig.name,
                userManager.userToCookie(user),
                expires = None,
                maxAge = cookieConfig.maxAge,
                domain = cookieConfig.domain,
                path = cookieConfig.path,
                secure = cookieConfig.secure,
                httpOnly = cookieConfig.httpOnly,
                extension = cookieConfig.extension
              )
              resp.map(_.addCookie(cookie)).value
            }
        }

        OptionT(respf)
      }
  }
}

trait DoormanMiddleware {

  def userFromRequest[F[_]: Monad, User](
    r: Request[F],
    userManager: UserManager[F, User],
    cookieName: String): F[(Boolean, User)] =
    userFromCookie(r, userManager, cookieName).flatMap {
      case Some(user) =>
        Monad[F].pure((true, user))
      case None =>
        userManager.create.map(user => (false, user))
    }

  private def userFromCookie[F[_]: Monad, User](
    req: Request[F],
    userManager: UserManager[F, User],
    cookieName: String
  ): F[Option[User]] = {
    req.cookies.filter(_.name == cookieName).headOption.map(_.content)
    .fold[F[Option[User]]](Monad[F].pure(None))(c => userManager.cookieToUser(c))
     
  }
}
