package com.akolov.doorman

import org.http4s.server.middleware.CORSConfig

import scala.concurrent.duration._

trait Testing {

  val corsConfig: CORSConfig = CORSConfig(
    anyOrigin = true,
    allowedOrigins = Set[String](),
    allowCredentials = true,
    maxAge = 1.day.toSeconds
  )
}
