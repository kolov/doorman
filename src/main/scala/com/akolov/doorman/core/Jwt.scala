package com.akolov.doorman.core

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import scala.util.Try

case class JwtIssuer(name: String, algorithm: Algorithm) {

  private[this] val verifier = JWT.require(algorithm)
    .withIssuer(name)
    .build()


}
