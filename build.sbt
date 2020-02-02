import sbt.Keys.credentials
import sbt.{url, Credentials, Developer, Path, ScmInfo}

val Http4sVersion = "0.21.0-M6"
val Specs2Version = "4.7.1"
val LogbackVersion = "1.2.3"
val GoogleOauthClientVersion = "1.22.0"
val CirceVersion = "0.12.3"
val scalaLoggingVersion = "3.9.2"

lazy val scala212 = "2.12.10"
lazy val scala213 = "2.13.0"

lazy val supportedScalaVersions = List(scala212, scala213)

ThisBuild / organization := "com.akolov"
ThisBuild / name := "doorman"
ThisBuild / scalaVersion := scala213
ThisBuild / publishMavenStyle := true
ThisBuild / credentials += Credentials(Path.userHome / ".sonatype" / ".credentials")
ThisBuild / description := "Oauth2 authentication and user session middleware for http4s"
ThisBuild / licenses := Seq("MIT License" -> url("https://github.com/kolov/doorman/blob/master/LICENSE"))
ThisBuild / useGpg := true
ThisBuild / homepage := Some(url("https://github.com/kolov/doorman"))
releaseCrossBuild := true
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
  addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.0" cross CrossVersion.full)
)

lazy val testDependencies = Seq(
  "org.specs2" %% "specs2-core" % Specs2Version % "test",
  "org.specs2" %% "specs2-mock" % Specs2Version % "test",
  "com.codecommit" %% "cats-effect-testing-specs2" % "0.3.0" % "test"
)

lazy val core = (project in file("core")).settings(
  name := "doorman",
  commonSettings,
  libraryDependencies ++= Seq(
    "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
    "org.http4s" %% "http4s-blaze-server" % Http4sVersion,
    "org.http4s" %% "http4s-blaze-client" % Http4sVersion,
    "org.http4s" %% "http4s-circe" % Http4sVersion,
    "org.http4s" %% "http4s-dsl" % Http4sVersion,
    "io.circe" %% "circe-generic" % CirceVersion,
    "io.circe" %% "circe-parser" % CirceVersion
  ) ++ testDependencies,
  publishTo := Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2"),
  credentials += Credentials(Path.userHome / ".sonatype" / ".credentials"),
  crossScalaVersions := supportedScalaVersions
)

lazy val demo = (project in file("demo"))
  .dependsOn(core)
  .settings(
    commonSettings,
    publish / skip := true,
    libraryDependencies ++= Seq(
      "com.auth0" % "java-jwt" % "3.2.0",
      "com.github.pureconfig" %% "pureconfig" % "0.12.2",
      "ch.qos.logback" % "logback-classic" % LogbackVersion
    ) ++ testDependencies
  )
  .enablePlugins(JavaAppPackaging)

lazy val root = (project in file("."))
  .aggregate(demo, core)
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil
  )

lazy val docs = project
  .in(file("project-docs")) // important: it must not be docs/
  .dependsOn(demo)
  .enablePlugins(MdocPlugin)
  .settings(
    crossScalaVersions := Nil,
    publish / skip := true

    //    mdocOut := new java.io.File("README.md")
  )
