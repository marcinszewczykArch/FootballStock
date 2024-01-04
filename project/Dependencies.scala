import sbt._

object Dependencies {
  val ScalaTest = "org.scalatest" %% "scalatest" % Versions.scalaTestVersion
  val catsCore = "org.typelevel" %% "cats-core" % Versions.catsVersion
  val catsEffects = "org.typelevel" %% "cats-effect" % Versions.catsEffectsVersion
  val fs2 = "co.fs2" %% "fs2-core" % Versions.fs2Version
  val munit = "org.typelevel" %% "munit-cats-effect-3" % Versions.munitVersion
  val sttp = Seq(
    "com.softwaremill.sttp.client3" %% "cats" % Versions.sttpVersion,
    "com.softwaremill.sttp.client3" %% "http4s-backend" % Versions.sttpVersion,
    "com.softwaremill.sttp.client3" %% "circe" % Versions.sttpVersion
  )
  val typesafeConfig = "com.typesafe" % "config" % Versions.typesafeConfigVersion

  val pureconfig = Seq(
    "com.github.pureconfig" %% "pureconfig" % "0.17.4",
    "eu.timepit" %% "refined-pureconfig" % "0.11.0"
  )

  val logging = Seq(
    "org.typelevel" %% "log4cats-slf4j" % "2.5.0",
    "org.typelevel" %% "log4cats-noop" % "2.5.0" % Test,
    "ch.qos.logback" % "logback-classic" % "1.4.14"
  )

  val scanamo = Seq("org.scanamo" %% "scanamo" % Versions.scanamoVersion,
  "org.scanamo" %% "scanamo-testkit" % Versions.scanamoVersion % Test
  )


}

object Versions {
  val scalaTestVersion = "3.2.15"
  val catsVersion = "2.10.0"
  val catsEffectsVersion = "3.5.2"
  val fs2Version = "3.8.0"
  val munitVersion = "1.0.7"
  val sttpVersion = "3.9.0"
  val typesafeConfigVersion = "1.4.3"
  val scanamoVersion = "1.0.0-M23"
}