import Dependencies.*
import sbtassembly.MergeStrategy

ThisBuild / organization := "com.github.marcinszewczykArch"
ThisBuild / scalaVersion := "2.13.11"
ThisBuild / version := "1.0-SNAPSHOT"

lazy val root = project
  .in(file("."))
  .settings(
    name := "FootballStock",
    libraryDependencies ++= dependencies,
    assembly / mainClass := Some("Main"),
    assembly / assemblyJarName := name.value + ".jar",
    assembly / assemblyMergeStrategy := defaultMergeStrategy
  )
  .enablePlugins(JavaAppPackaging)

val dependencies =
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
    testContainer ++
    bcrypt

val firstMergeStrategy: String => MergeStrategy = {
  case PathList(ps @ _*) if ps.last endsWith ".properties" => MergeStrategy.concat
  case PathList(ps @ _*) if ps.last endsWith ".conf" => MergeStrategy.concat
  case PathList(ps @ _*) if ps.last == "schema" => MergeStrategy.concat
  case PathList(ps @ _*) if ps.last == "module-info.class" => MergeStrategy.discard
  case x                                                   =>
    val oldStrategy = (assembly / assemblyMergeStrategy).value
    oldStrategy(x)
}
val defaultMergeStrategy: String => MergeStrategy = {
  case x if Assembly.isConfigFile(x) =>
    MergeStrategy.concat
  case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
    MergeStrategy.rename
  case PathList("META-INF", xs @ _*) =>
    (xs map {_.toLowerCase}) match {
      case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
        MergeStrategy.discard
      case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
        MergeStrategy.discard
      case "plexus" :: xs =>
        MergeStrategy.discard
      case "services" :: xs =>
        MergeStrategy.filterDistinctLines
      case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
        MergeStrategy.filterDistinctLines
      case _ => MergeStrategy.deduplicate
    }
  case _ => MergeStrategy.deduplicate
}
