package com.akolov.doorman.core

import cats._
import cats.data._
import cats.implicits._

class UserService[F[_] : Monad, User](doormanClient: Doorman[F, User]) {

  def createNewUser: F[UserAndCookie[User]] = {
    doormanClient.create.map(u => UserAndCookie(u, Some(doormanClient.toCookie(u))))
  }

  val userService: Kleisli[OptionT[F, ?], Option[String], UserAndCookie[User]] = Kleisli { cookieValue =>

    val respUserFromCookie: OptionT[F, UserAndCookie[User]] = for {
      cookie <- OptionT.fromOption[F](cookieValue)
      user <- OptionT(doormanClient.toUser(cookie))
      resp = UserAndCookie(user, None)
    } yield resp

    respUserFromCookie.orElseF(createNewUser.map(Some(_)))
  }


}