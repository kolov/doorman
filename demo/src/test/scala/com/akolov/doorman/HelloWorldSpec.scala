package com.akolov.doorman

import cats.effect.IO
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.Router
import org.specs2.matcher.MatchResult
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification


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


  val sessionManager = ServerConfig.sessionManager

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
