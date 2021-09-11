package com.akolov.doorman.demo

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits.*
import org.http4s.blaze.server.BlazeServerBuilder

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    for {
      appConfig <- IO.fromEither(AppConfig.demoAppConfig.leftMap(e => new Exception(e.toString)))
      app = new DemoApp(appConfig.oauthProviders.get)
      code <- BlazeServerBuilder[IO]
               .bindHttp(8080, "0.0.0.0")
               .withHttpApp(app.service)
               .serve
               .compile
               .drain
               .map(_ => ExitCode.Success)
    } yield code

}
