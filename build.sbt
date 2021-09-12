import sbt.Keys.credentials
import sbt.{url, Credentials, Developer, Path, ScmInfo}

val Http4sVersion = "0.23.2"
val Specs2Version = "5.0.0-RC-09"
val LogbackVersion = "1.2.5"
val GoogleOauthClientVersion = "1.22.0"
val CirceVersion = "0.15.0-M1"
val CatsEffectVersion = "3.2.5"
val scalaLoggingVersion = "3.9.4"

lazy val scala3 = "3.0.2"

ThisBuild / organization      := "com.akolov"
ThisBuild / scalaVersion      := scala3
ThisBuild / publishMavenStyle := true
ThisBuild / credentials += Credentials(Path.userHome / ".sonatype" / ".credentials")
ThisBuild / description          := "Oauth2 authentication and user session middleware for http4s"
ThisBuild / licenses             := Seq("MIT License" -> url("https://github.com/kolov/doorman/blob/master/LICENSE"))
ThisBuild / useGpg               := true
ThisBuild / homepage             := Some(url("https://github.com/kolov/doorman"))
releaseCrossBuild                := true
ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
  else Some("releases" at nexus + "service/local/staging/deploy/maven2")
}

ThisBuild / scmInfo := Some(
  ScmInfo(
    url("https://github.com/kolov/doorman"),
    "scm:git@github.com:kolov/doorman.git"
  )
)

ThisBuild / developers := List(
  Developer(
    id = "kolov",
    name = "Assen Kolov",
    email = "assen.kolov@gmail.com",
    url = url("https://github.com/kolov")
  )
)

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",         // emit warning and location for usages of deprecated APIs
    "-explain",             // explain errors in more detail
    "-explain-types",       // explain type errors in more detail
    "-feature",             // emit warning and location for usages of features that should be imported explicitly
    "-indent",              // allow significant indentation.
    "-new-syntax",          // require `then` and `do` in control expressions.
    "-print-lines",         // show source code line numbers.
    "-unchecked",           // enable additional warnings where generated code depends on assumptions
    "-Ykind-projector",     // allow `*` as wildcard to be compatible with kind projector
    "-Xfatal-warnings",     // fail the compilation if there are any warnings
    "-Xmigration",          // warn about constructs whose behavior may have changed since version
//    "-source:3.0-migration"
  )
)

lazy val testDependencies = Seq(
  "org.specs2" %% "specs2-core" % Specs2Version % "test"
//  "org.specs2" %% "specs2-mock" % Specs2Version % "test",
//  "org.typelevel" %% "cats-effect-testing-specs2" % "1.2.0" % "test"
)

lazy val `doorman-core` = (project in file("core")).settings(
  name := "doorman-core",
  commonSettings,
  scalaVersion      := scala3,
  libraryDependencies ++= Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
    "org.http4s" %% "http4s-blaze-server"           % Http4sVersion,
    "org.http4s" %% "http4s-blaze-client"           % Http4sVersion,
    "org.http4s" %% "http4s-circe"                  % Http4sVersion,
    "org.http4s" %% "http4s-dsl"                    % Http4sVersion,
    "io.circe" %% "circe-generic"                   % CirceVersion,
//    "io.circe" %% "circe-literal"                   % CirceVersion,
    "io.circe" %% "circe-parser"            % CirceVersion,
    "org.typelevel" %% "cats-effect-kernel" % CatsEffectVersion,
    "org.typelevel" %% "cats-effect-std"    % CatsEffectVersion,
    "org.typelevel" %% "cats-effect"        % CatsEffectVersion
  ) ++ testDependencies,
  credentials += Credentials(Path.userHome / ".sonatype" / ".credentials")
)

lazy val demo = (project in file("demo"))
  .dependsOn(`doorman-core`)
  .settings(
    commonSettings,
    publish / skip := true,
    libraryDependencies ++= Seq(
      "com.auth0"                                  % "java-jwt"        % "3.18.1",
      "com.github.pureconfig" %% "pureconfig-core" % "0.16.0",
      "ch.qos.logback"                             % "logback-classic" % LogbackVersion
    ) ++ testDependencies
  )
  .enablePlugins(JavaAppPackaging)

lazy val root = (project in file("."))
  .aggregate(demo, `doorman-core`)
  .settings(
    name           := "doorman",
    publish / skip := true
  )

lazy val docs = project
  .in(file("project-docs")) // important: it must not be docs/
  .dependsOn(demo)
  .enablePlugins(MdocPlugin)
  .settings(
    publish / skip := true

    //    mdocOut := new java.io.File("README.md")
  )
