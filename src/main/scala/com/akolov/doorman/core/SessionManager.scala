package com.akolov.doorman.core

import cats._
import cats.data._
import cats.effect.{Effect, IO}
import cats.implicits._
import com.akolov.doorman.{AppUser, DoormanClient}
import com.akolov.doorman.ServerConfig.doormanClient
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Cookie
import org.http4s.server.AuthMiddleware
import org.http4s.{AuthedRequest, Request, Response, ResponseCookie, Status}

case class UserAndCookie[User](user: User, cookie: Option[String])

class SessionManager[F[_] : Effect, User](doormanClient: DoormanClient[F, User])
  extends Http4sDsl[F] {

  // userService: Kleisli[OptionT[F, ?], Option[String], UserAndCookie[User]]
  private val CookieName = "auth-cookie"

  val userService: Kleisli[OptionT[F, ?], Option[String], UserAndCookie[User]] = new UserService[F, User](doormanClient).userService


  val middleware: AuthMiddleware[F, User] = { (service: Kleisli[OptionT[F, ?], AuthedRequest[F, User], Response[F]]) =>
    Kleisli { r: Request[F] =>
      val reqAndCookie = userFromCookie.map { userAndCookie =>
        (AuthedRequest(userAndCookie.user, r), userAndCookie.cookie)
      }
      runAndKeep(reqAndCookie, service).run(r).map {
        case (response, optCookie) => optCookie.map(v => response.addCookie(CookieName, v)).getOrElse(response)
      }
    }
  }

  def userRegistered(user: User, response: Response[F]): F[Response[F]] =
    Monad[F].pure(response.addCookie(CookieName, ""))

  private val userFromCookie: Kleisli[OptionT[F, ?], Request[F], UserAndCookie[User]] = Kleisli { (req: Request[F]) =>
    val cookieValue: Option[String] = Cookie.from(req.headers)
      .map(_.values.head)
      .filter(x => x.name.toString == CookieName)
      .headOption
      .map(_.content)

    userService.run(cookieValue)
  }


  private def defaultAuthFailure[F[_]](implicit F: Applicative[F]): Request[F] => F[Response[F]] =
    _ => F.pure(Response[F](Status.Unauthorized))


  private def runAndKeep[F[_] : Monad, A, B, B1, C](k1: Kleisli[F, A, (B, B1)], k2: Kleisli[F, B, C]): Kleisli[F, A, (C, B1)] =
    Kleisli(a => k1.run(a).flatMap { case (b, b1) => k2.run(b).map((_, b1)) })

}





