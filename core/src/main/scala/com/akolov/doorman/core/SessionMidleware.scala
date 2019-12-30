package com.akolov.doorman.core

import cats._
import cats.data._
import cats.effect.Effect
import cats.implicits._
import org.http4s.headers.Cookie
import org.http4s.server.{AuthMiddleware, Middleware}
import org.http4s.{AuthedRequest, Request, Response}

object UserTrackingMiddleware extends MiddlewareHelper {
  type UserTrackingMiddleware[F[_], _] = Middleware[OptionT[F, ?], Request[F], Response[F], Request[F], Response[F]]

  def apply[F[_]: Effect, User](userManager: UserManager[F, User]): UserTrackingMiddleware[F, User] = {
    (service: Kleisli[OptionT[F, ?], Request[F], Response[F]]) =>
      Kleisli { r: Request[F] =>
        OptionT(userFromRequest(r, userManager).flatMap {
          case (isOld, user) =>
            val resp: OptionT[F, Response[F]] = service.run(r)
            if (isOld) {
              resp.value
            } else {
              resp.map(_.addCookie(userManager.cookieName, userManager.userToCookie(user))).value
            }
        })
      }
  }
}

object DoormanAuthMiddleware extends MiddlewareHelper {

  def apply[F[_]: Effect, User](userManager: UserManager[F, User]): AuthMiddleware[F, User] = {
    (service: Kleisli[OptionT[F, ?], AuthedRequest[F, User], Response[F]]) =>
      Kleisli { r: Request[F] =>
        val respf: F[Option[Response[F]]] = userFromRequest(r, userManager).flatMap {
          case (isOld, user) =>
            val resp: OptionT[F, Response[F]] = service.run(AuthedRequest(user, r))
            if (isOld) {
              resp.value
            } else {
              resp.map(_.addCookie(userManager.cookieName, userManager.userToCookie(user))).value
            }
        }

        OptionT(respf)
      }
  }
}

trait MiddlewareHelper {

  def userFromRequest[F[_]: Effect, User](r: Request[F], userManager: UserManager[F, User]): F[(Boolean, User)] =
    userFromCookie(r, userManager).flatMap {
      case Some(user) =>
        Monad[F].pure((true, user))
      case None =>
        userManager.create.map(user => (false, user))
    }

  private def userFromCookie[F[_]: Effect, User](
    req: Request[F],
    userManager: UserManager[F, User]
  ): F[Option[User]] = {
    Cookie
      .from(req.headers)
      .flatMap(_.values.filter(_.name == userManager.cookieName).headOption)
      .map(_.content)
      .fold[F[Option[User]]](Monad[F].pure(None))(c => userManager.cookieToUser(c))
  }

}
