import sbt.*

object Dependencies {

  val scalaTest = Seq(
    "org.scalatest" %% "scalatest" % Versions.scalaTestVersion
  )

  val catsCore = Seq(
    "org.typelevel" %% "cats-core" % Versions.catsVersion
  )

  val catsEffects = Seq(
    "org.typelevel" %% "cats-effect"         % Versions.catsEffectsVersion,
    "org.typelevel" %% "cats-effect-testkit" % "3.5.2" % Test,
    "org.typelevel" %% "cats-effect-testing-scalatest" % "1.5.0" % Test
  )

  val fs2 = Seq(
    "co.fs2" %% "fs2-core" % Versions.fs2Version
  )

  val munit = Seq(
    "org.typelevel" %% "munit-cats-effect-3" % Versions.munitVersion
  )

  val sttp = Seq(
    "com.softwaremill.sttp.client3" %% "cats"           % Versions.sttpVersion,
    "com.softwaremill.sttp.client3" %% "http4s-backend" % Versions.sttpVersion,
    "com.softwaremill.sttp.client3" %% "circe"          % Versions.sttpVersion
  )

  val typesafeConfig = Seq(
    "com.typesafe" % "config" % Versions.typesafeConfigVersion
  )

  val pureconfig = Seq(
    "com.github.pureconfig" %% "pureconfig"         % "0.17.4",
    "eu.timepit"            %% "refined-pureconfig" % "0.11.0"
  )

  val logging = Seq(
    "org.typelevel" %% "log4cats-slf4j"  % "2.5.0",
    "org.typelevel" %% "log4cats-noop"   % "2.5.0" % Test,
    "ch.qos.logback" % "logback-classic" % "1.4.14"
  )

  val scanamo = Seq(
    "org.scanamo" %% "scanamo"         % Versions.scanamoVersion,
    "org.scanamo" %% "scanamo-testkit" % Versions.scanamoVersion % Test
  )

  val circe = Seq(
    "io.circe" %% "circe-core"           % Versions.circeVersion,
    "io.circe" %% "circe-generic"        % Versions.circeVersion,
    "io.circe" %% "circe-generic-extras" % Versions.circeVersion,
    "io.circe" %% "circe-optics"         % Versions.circeVersion,
    "io.circe" %% "circe-parser"         % Versions.circeVersion
  )

  val tapir = Seq(
    "com.softwaremill.sttp.tapir" %% "tapir-cats"              % Versions.tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % Versions.tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"     % Versions.tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-openapi-docs"      % Versions.tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui"        % Versions.tapirVersion,
    "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % Versions.tapirVersion
  )

  val http4s = Seq(
    "org.http4s" %% "http4s-blaze-server" % Versions.http4sBlazeVersion,
    "org.http4s" %% "http4s-blaze-client" % Versions.http4sBlazeVersion,
    "org.http4s" %% "http4s-ember-server" % Versions.http4sVersion,
    "org.http4s" %% "http4s-circe"        % Versions.http4sVersion,
    "org.http4s" %% "http4s-dsl"          % Versions.http4sVersion
  )

  val testContainer = Seq(
    "com.dimafeng"   %% "testcontainers-scala-scalatest"     % Versions.testcontainers % Test,
    "com.dimafeng"   %% "testcontainers-scala-mockserver"    % Versions.testcontainers % Test,
    "com.dimafeng"   %% "testcontainers-scala-localstack-v2" % Versions.testcontainers % Test,
    "com.amazonaws"   % "aws-java-sdk-core"                  % "1.12.425",
    "org.mock-server" % "mockserver-client-java"             % "5.15.0",
    "io.circe"       %% "circe-literal"                      % "0.14.4"                % Test
  )

  val bcrypt = Seq("com.github.t3hnar" %% "scala-bcrypt" % "4.3.0")

}

object Versions {
  val scalaTestVersion      = "3.2.15"
  val catsVersion           = "2.10.0"
  val catsEffectsVersion    = "3.5.2"
  val fs2Version            = "3.8.0"
  val munitVersion          = "1.0.7"
  val sttpVersion           = "3.9.0"
  val typesafeConfigVersion = "1.4.3"
  val scanamoVersion        = "1.0.0-M23"
  val circeVersion          = "0.14.1"
  val tapirVersion          = "1.4.0"
  val http4sVersion         = "0.23.18"
  val http4sBlazeVersion    = "0.23.14"
  val testcontainers        = "0.40.12"
}
