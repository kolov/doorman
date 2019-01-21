package com.akolov.doorman

import cats.implicits._
import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.server.blaze.BlazeServerBuilder


object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    for {
      myService <- ServerConfig.theService
      code <- BlazeServerBuilder[IO]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(myService.app)
        .serve.compile.drain.as(ExitCode.Success)
    } yield code

}
