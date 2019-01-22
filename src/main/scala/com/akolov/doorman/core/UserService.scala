package com.akolov.doorman.core

import cats.data.{Kleisli, OptionT}
import cats.effect.IO
import com.akolov.doorman.AppUser

class UserService(jwtIssuer: JwtIssuer) {

  def createNewUser: OptionT[IO, UserAndCookie[AppUser]] = {
    val user = AppUser.create[IO].map(u =>
      UserAndCookie(u, Some(jwtIssuer.toCookie(UserData(u.uuid, None)))))
    OptionT.liftF(user)
  }


  val userService: Kleisli[OptionT[IO, ?], Option[String], UserAndCookie[AppUser]] = Kleisli { cookieValue =>

    val userFromCookie: Option[AppUser] = for {
      cookieValue <- cookieValue
      userData <- jwtIssuer.getUserData(cookieValue) match {
        case Left(e) => println(s"Invalid jwt: ${e.getMessage} parsing $cookieValue"); None
        case Right(r) => Some(r)
      }
    } yield AppUser.forProvider(userData.uuid, userData.provider)

    userFromCookie match {
      case Some(user) => OptionT.fromOption[IO](Some(UserAndCookie(user, None)))
      case None => createNewUser
    }
  }


}