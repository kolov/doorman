package com.akolov.doorman.core

import cats._
import cats.data._
import cats.effect.Effect
import cats.implicits._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Cookie
import org.http4s.server.AuthMiddleware
import org.http4s.{AuthedRequest, Request, Response, ResponseCookie, Status}

case class UserAndCookie[User](user: User, cookie: Option[String])

class SessionManager[F[_] : Effect, User](userService: Kleisli[OptionT[F, ?], Option[String], UserAndCookie[User]]) extends Http4sDsl[F] {


  val CookieName = "auth-cookie"

  def verifyLogin(request: Request[F]): F[Either[String, String]] = ??? // gotta figure out how to do the form

  val userFromCookie: Kleisli[OptionT[F, ?], Request[F], UserAndCookie[User]] = Kleisli { (req: Request[F]) =>
    val cookieValue: Option[String] = Cookie.from(req.headers)
      .map(_.values.head)
      .filter(x => x.name.toString == CookieName)
      .headOption
      .map(_.content)

    userService.run(cookieValue)
  }

  val logIn: Kleisli[F, Request[F], Response[F]] = Kleisli({ request =>
    verifyLogin(request: Request[F]).flatMap(_ match {
      case Left(error) =>
        Forbidden(error)
      case Right(user) => {
        val message = ???
        Ok("Logged in!").map(_.addCookie(ResponseCookie("authcookie", message)))
      }
    })
  })

  def defaultAuthFailure[F[_]](implicit F: Applicative[F]): Request[F] => F[Response[F]] =
    _ => F.pure(Response[F](Status.Unauthorized))

  val middleware: AuthMiddleware[F, User] = { (service: Kleisli[OptionT[F, ?], AuthedRequest[F, User], Response[F]]) =>
    Kleisli { r: Request[F] =>
      val reqAndCookie = userFromCookie.map { userAndCookie =>
        (AuthedRequest(userAndCookie.user, r), userAndCookie.cookie)
      }
      //service.mapF(o => OptionT.liftF(o.getOrElse(Response[F](Status.NotFound)))))
      //        .mapF(o => OptionT.liftF(o.getOrElseF(defaultAuthFailure(r))))
      runAndKeep(reqAndCookie, service).run(r).map {
        case (response, optCookie) => optCookie.map(v => response.addCookie(CookieName, v)).getOrElse(response)
      }

    }
  }

  def runAndKeep[F[_] : Monad, A, B, B1, C](k1: Kleisli[F, A, (B, B1)], k2: Kleisli[F, B, C]): Kleisli[F, A, (C, B1)] =
    Kleisli(a => k1.run(a).flatMap { case (b, b1) => k2.run(b).map((_, b1)) })

}





