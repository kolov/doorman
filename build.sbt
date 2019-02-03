val Http4sVersion = "0.20.0-M3"
val Specs2Version = "4.1.0"
val LogbackVersion = "1.2.3"
val GoogleOauthClientVersion = "1.22.0"
val CirceVersion = "0.10.1"

lazy val root = (project in file("."))
  .settings(
    organization := "com.akolov",
    name := "doorman",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.12.7",
    libraryDependencies ++= Seq(
      "org.http4s"              %% "http4s-blaze-server"        % Http4sVersion,
      "org.http4s"              %% "http4s-blaze-client"        % Http4sVersion,
      "org.http4s"              %% "http4s-circe"               % Http4sVersion,
      "org.http4s"              %% "http4s-dsl"                 % Http4sVersion,
      "ch.qos.logback"          %  "logback-classic"            % LogbackVersion,
      "com.auth0"               % "java-jwt"                    % "3.2.0",
      "com.google.oauth-client" % "google-oauth-client"         % GoogleOauthClientVersion,
      "com.google.http-client"  % "google-http-client-jackson"  % GoogleOauthClientVersion,
      "com.typesafe"            % "config"                      % "1.3.2",
      "org.specs2"              %% "specs2-core"                % Specs2Version % "test",
      "org.specs2"              %% "specs2-mock"                % Specs2Version % "test",
      "io.circe"                %% "circe-generic"              % CirceVersion,
      "io.circe"                %% "circe-parser"               % CirceVersion,
    ),
    addCompilerPlugin("org.spire-math" %% "kind-projector"     % "0.9.6"),
    addCompilerPlugin("com.olegpy"     %% "better-monadic-for" % "0.2.4")
  )

