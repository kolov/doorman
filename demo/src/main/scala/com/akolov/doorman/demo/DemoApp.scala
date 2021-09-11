package com.akolov.doorman.demo

import cats.data.Kleisli
import cats.effect.IO
import cats.implicits.*
import com.akolov.doorman.core.OAuthProviderConfig
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.implicits.*
import org.http4s.server.middleware.{CORS, CORSConfig}
import org.http4s.{Request, Response}

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.*
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

class DemoApp(providerLookup: String => Option[OAuthProviderConfig]) {

  val corsConfig: CORSConfig =
    CORSConfig.default
      .withAllowedOrigins(_ == "http://localhost:8080")
      .withAllowCredentials(true)
      .withMaxAge(1.day)

  val blockingEC: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  lazy val usersManager = DemoUserManager

  lazy val httpClient = BlazeClientBuilder[IO](global).resource

  lazy val demoService = new DemoService(usersManager, providerLookup, httpClient)

  lazy val service: Kleisli[IO, Request[IO], Response[IO]] =
    CORS(demoService.routes, corsConfig).orNotFound

}
