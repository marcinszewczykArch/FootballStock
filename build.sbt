import Dependencies._

ThisBuild / organization := "com.github.marcinszewczykArch"
ThisBuild / scalaVersion := "2.13.11"
ThisBuild / version := "1.0-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .settings(
    name := "FootballStock",
    libraryDependencies ++=
      Seq(ScalaTest, catsCore, catsEffects, fs2, munit, typesafeConfig, compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")) ++
        sttp ++
        pureconfig ++
        logging ++
        scanamo ++
        circe
  )