package com.akolov.doorman.core

import cats._
import cats.data._
import cats.implicits._
import cats.effect.Effect
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Cookie
import org.http4s.server.{AuthMiddleware, Middleware}
import org.http4s.{AuthedRequest, Request, Response}

case class UserAndCookie[User](user: User, cookie: Option[String])

object SessionManager {
  def apply[F[_]: Effect, User](doorman: Doorman[F, User], doormanConfig: DoormanConfig) =
    new SessionManager(doorman, doormanConfig)
}

class SessionManager[F[_]: Effect, User](val doorman: Doorman[F, User], doormanConfig: DoormanConfig)
    extends Http4sDsl[F] {

  private val CookieName = doormanConfig.cookieName

  val userService: Kleisli[OptionT[F, ?], Option[String], UserAndCookie[User]] =
    new UserService[F, User](doorman).userService

  val cookieMiddleware: Middleware[OptionT[F, ?], Request[F], Response[F], Request[F], Response[F]] = {
    (service: Kleisli[OptionT[F, ?], Request[F], Response[F]]) =>
      Kleisli { r: Request[F] =>
        val reqAndCookie = userFromCookie.map { userAndCookie =>
          (r, userAndCookie.cookie)
        }
        runAndKeep(reqAndCookie, service).run(r).map {
          case (response, optCookie) => optCookie.map(v => response.addCookie(CookieName, v)).getOrElse(response)
        }
      }
  }
  val userProviderMiddleware: AuthMiddleware[F, User] = {
    (service: Kleisli[OptionT[F, ?], AuthedRequest[F, User], Response[F]]) =>
      Kleisli { r: Request[F] =>
        val reqAndCookie = userFromCookie.map { userAndCookie =>
          (AuthedRequest(userAndCookie.user, r), userAndCookie.cookie)
        }
        runAndKeep(reqAndCookie, service).run(r).map {
          case (response, optCookie) => optCookie.map(v => response.addCookie(CookieName, v)).getOrElse(response)
        }
      }
  }

  def addUserCookie(user: User, response: Response[F]): Response[F] =
    response.addCookie(CookieName, doorman.toCookie(user))

  private val userFromCookie: Kleisli[OptionT[F, ?], Request[F], UserAndCookie[User]] = Kleisli { (req: Request[F]) =>
    val cookieValue: Option[String] = Cookie
      .from(req.headers)
      .map(_.values.head)
      .filter(x => x.name.toString == CookieName)
      .headOption
      .map(_.content)

    userService.run(cookieValue)
  }

  private def runAndKeep[F[_]: Monad, A, B, B1, C](
    k1: Kleisli[F, A, (B, B1)],
    k2: Kleisli[F, B, C]): Kleisli[F, A, (C, B1)] =
    Kleisli(a => k1.run(a).flatMap { case (b, b1) => k2.run(b).map((_, b1)) })

}
