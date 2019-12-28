package com.akolov.doorman

import cats.effect.IO
import cats.effect.specs2.CatsIO
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.Router
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import scala.concurrent.ExecutionContext.Implicits.global

class HelloWorldSpec extends Specification with CatsIO with Mockito with Testing {

  "The Demo application" should {

    "redirect request to /" in new TestContext {
      serve(Request[IO](Method.GET, uri"/")).status must beEqualTo(Status.TemporaryRedirect)
    }

    "serve to request to /index.html" in new TestContext {
      serve(Request[IO](Method.GET, uri"/index.html")).status must beEqualTo(Status.Ok)
    }

  }

  class TestContext extends Scope {
    private val demoConfig = AppConfig.demoAppConfig.right.get
    val serverConfig = new DemoApp(demoConfig)
    val sessionManager = serverConfig.sessionManager
    val routes = Router("/" -> new DemoService[IO](sessionManager).routes).orNotFound

    def serve(request: Request[IO]): Response[IO] =
      routes(request).unsafeRunSync()
  }

}
