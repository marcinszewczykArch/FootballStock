import Dependencies._

ThisBuild / organization := "com.github.marcinszewczykArch"
ThisBuild / scalaVersion := "2.13.11"
ThisBuild / version := "1.0-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .settings(
    name := "FootballStock",
    libraryDependencies ++=
      Seq(compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")) ++
        scalaTest ++
        catsCore ++
        catsEffects ++
        fs2 ++
        munit ++
        typesafeConfig ++
        sttp ++
        pureconfig ++
        logging ++
        scanamo ++
        circe ++
        tapir ++
        http4s ++
        testContainer
  )
