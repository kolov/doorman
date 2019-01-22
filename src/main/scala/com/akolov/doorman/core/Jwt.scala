package com.akolov.doorman.core

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import scala.util.Try

case class UserData(uuid: String, provider: Option[String])

case class JwtIssuer(name: String, algorithm: Algorithm) {


  private[this] val verifier = JWT.require(algorithm)
    .withIssuer(name)
    .build()

  def toCookie(userData: UserData) = {
    val builder = JWT.create()
      .withIssuer(name)
      .withClaim("sub", userData.uuid)

    userData.provider
      .map(p => builder.withClaim("provider", p))
      .getOrElse(builder)
      .sign(algorithm)
  }

  def getUserData(token: String): Either[Throwable, UserData] =
    Try(verifier.verify(token)).toEither.map { payload =>
      UserData(payload.getSubject, Option(payload.getClaim("provider").asString))
    }


}
