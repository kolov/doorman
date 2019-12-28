package com.akolov.doorman.core

import cats._
import cats.data._
import cats.implicits._
import cats.effect.Effect
import cats.implicits._
import org.http4s.headers.Cookie
import org.http4s.server.{AuthMiddleware, Middleware}
import org.http4s.{AuthedRequest, Request, Response}

trait SessionManager[F[_], U] {
  val authUserMiddleware: AuthMiddleware[F, U]
  val userTrackingMiddleware: Middleware[OptionT[F, ?], Request[F], Response[F], Request[F], Response[F]]
}

object SessionManager {

  def apply[F[_]: Effect, User](userManager: UserManager[F, User]) =
    new SessionManager[F, User] {

      override val authUserMiddleware: AuthMiddleware[F, User] = {
        (service: Kleisli[OptionT[F, ?], AuthedRequest[F, User], Response[F]]) =>
          Kleisli { r: Request[F] =>
            val respf: F[Option[Response[F]]] = userFromRequest(r).flatMap {
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

      override val userTrackingMiddleware
        : Middleware[OptionT[F, ?], Request[F], Response[F], Request[F], Response[F]] = {
        (service: Kleisli[OptionT[F, ?], Request[F], Response[F]]) =>
          Kleisli { r: Request[F] =>
            OptionT(userFromRequest(r).flatMap {
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

      def userFromRequest(r: Request[F]): F[(Boolean, User)] = userFromCookie(r).flatMap {
        case Some(user) =>
          Monad[F].pure((true, user))
        case None =>
          userManager.create.map(user => (false, user))
      }

      private def userFromCookie(req: Request[F]): F[Option[User]] = {
        Cookie
          .from(req.headers)
          .flatMap(_.values.filter(_.name == userManager.cookieName).headOption)
          .map(_.content)
          .fold[F[Option[User]]](Monad[F].pure(None))(c => userManager.cookieToUser(c))
      }

    }
}
