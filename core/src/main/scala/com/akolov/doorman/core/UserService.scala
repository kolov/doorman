package com.akolov.doorman.core

import cats._
import cats.data._
import cats.implicits._

class UserService[F[_] : Monad, User](doormanClient: DoormanClient[F, User]) {

  def createNewUser: OptionT[F, UserAndCookie[User]] = {
    val user = doormanClient.create.map(u =>
      UserAndCookie(u, Some(doormanClient.toCookie(u))))
    OptionT.liftF(user)
  }

  val userService: Kleisli[OptionT[F, ?], Option[String], UserAndCookie[User]] = Kleisli { cookieValue =>

    val userFromCookie: Option[User] = for {
      cookieValue <- cookieValue
      userData <- doormanClient.toUser(cookieValue) match {
        case None => println(s"Invalid jwt: error parsing $cookieValue"); None
        case someu => someu
      }
    } yield userData

    userFromCookie match {
      case Some(user) => OptionT.fromOption[F](Some(UserAndCookie(user, None)))
      case None => createNewUser
    }
  }


}