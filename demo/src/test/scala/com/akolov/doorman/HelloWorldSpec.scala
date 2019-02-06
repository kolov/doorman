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


class HelloWorldSpec extends Specification
  with Mockito with Testing {

  "HelloWorld" >> {
    "return 200" >> {
      uriReturns200()
    }
    "return hello world" >> {
      uriReturnsHelloWorld()
    }
  }

  val executionContext: ExecutionContextExecutorService =
    ExecutionContext.fromExecutorService(Executors.newFixedThreadPool(8))

  implicit lazy val contextShift: ContextShift[IO] =
    IO.contextShift(executionContext) // ceremony 1

  val sessionManager = ServerConfig.sessionManager.run(AppConfig.config).unsafeRunSync

  private[this] val retHelloWorld: Response[IO] = {
    val getHW = Request[IO](Method.GET, Uri.uri("/api/v1/hello/world"))
    Router(
      "/api/v1" -> new DemoService[IO, AppUser](sessionManager).routes)
      .orNotFound(getHW).unsafeRunSync()
  }

  private[this] def uriReturns200(): MatchResult[Status] =
    retHelloWorld.status must beEqualTo(Status.Ok)

  private[this] def uriReturnsHelloWorld(): MatchResult[String] =
    retHelloWorld.as[String].unsafeRunSync() must contain("Hello, world")
}
