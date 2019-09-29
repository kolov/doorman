package com.akolov.doorman

import cats.effect.{ExitCode, IO, IOApp}
import org.http4s.server.blaze.BlazeServerBuilder

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    for {
      app <- ServerConfig.theService.run(AppConfig.config)
      code <- BlazeServerBuilder[IO]
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(app)
        .serve
        .compile
        .drain
        .map(_ => ExitCode.Success)
    } yield code

}