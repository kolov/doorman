package com.akolov.doorman.core

import cats._
import cats.data._
import cats.effect.Effect
import cats.implicits._
import org.http4s.dsl.Http4sDsl
import org.http4s.headers.Cookie
import org.http4s.server.AuthMiddleware
import org.http4s.{AuthedRequest, Request, Response, ResponseCookie, Status}

case class UserAndCookie[User](user: User, cookie: String)


class SessionManager[F[_] : Effect, User](userService: Kleisli[OptionT[F, ?], Option[String], User]) extends Http4sDsl[F] {


  def verifyLogin(request: Request[F]): F[Either[String, String]] = ??? // gotta figure out how to do the form

  val authUser: Kleisli[OptionT[F, ?], Request[F], User] = Kleisli { (req: Request[F]) =>

    val cookieValue: Option[String] = Cookie.from(req.headers)
      .map(_.values.head)
      .filter(x => x.name.toString == "auth-cookie")
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

  // type AuthMiddleware[F[_], T] =
  //    Middleware[OptionT[F, ?], AuthedRequest[F, T], Response[F], Request[F], Response[F]]
  //  type Middleware[F[_], A, B, C, D] = Kleisli[F, A, B] => Kleisli[F, C, D]
  //
  val middleware: AuthMiddleware[F, User] = { (service: Kleisli[OptionT[F, ?], AuthedRequest[F, User], Response[F]]) =>
    Kleisli { r: Request[F] =>
      authUser
        .map(user => AuthedRequest(user, r))
        .andThen(service.mapF(o => OptionT.liftF(o.getOrElse(Response[F](Status.NotFound)))))
        //        .mapF(o => OptionT.liftF(o.getOrElseF(defaultAuthFailure(r))))
        .run(r)
        .map(_.addCookie("auth-cookie", "xxx"))
    }
  }


}



