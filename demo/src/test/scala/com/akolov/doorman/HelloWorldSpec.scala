package com.akolov.doorman

import java.util.concurrent.Executors

import cats.effect.{ContextShift, IO}
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.Router
import org.specs2.matcher.MatchResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

import scala.concurrent.ExecutionContext.Implicits.global
class HelloWorldSpec extends Specification with Mockito with Testing {

  "HelloWorld" >> {
    "return 200" >> {
      uriReturns200()
    }

  }

  val executionContext: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

  implicit lazy val contextShift: ContextShift[IO] = IO.contextShift(executionContext)

  val sessionManager = ServerConfig.sessionManager.run(AppConfig.config).unsafeRunSync

  private[this] val retRoot: Response[IO] = {
    val getHW = Request[IO](Method.GET, Uri.uri("/"))
    Router("/" -> new DemoService[IO](sessionManager).routes)
      .orNotFound(getHW)
      .unsafeRunSync()
  }

  private[this] def uriReturns200(): MatchResult[Status] =
    retRoot.status must beEqualTo(Status.TemporaryRedirect)

}
