package com.akolov.doorman.core

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import com.akolov.doorman.AppUser


class UserService(jwtIssuer: JwtIssuer) {

  val userService: Kleisli[OptionT[IO, ?], Option[String], AppUser] = Kleisli { cookieValue =>

    val userFromCookie: Option[AppUser] = for {
      cookieValue <- cookieValue
      userData <- jwtIssuer.getUserData(cookieValue) match {
        case Left(e) => println(s"Invalid jwt: ${e.getMessage} parsing $cookieValue"); None
        case Right(r) => Some(r)
      }
    } yield AppUser.forProvider(userData.uuid, userData.provider)

    OptionT.fromOption[IO](userFromCookie)
      .orElseF(AppUser.create[IO].map(Some(_)))

  }
}