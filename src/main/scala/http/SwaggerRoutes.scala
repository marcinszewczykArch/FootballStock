package http

import cats.effect.IO
import http.club.ClubEndpoints
import http.event.EventEndpoints
import http.state.GameStateEndpoints
import http.player.PlayerEndpoints
import org.http4s.HttpRoutes
import sttp.apispec.openapi.circe.yaml.RichOpenAPI
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.{SwaggerUI, SwaggerUIOptions}

object SwaggerRoutes {

  val routes: IO[HttpRoutes[IO]] = IO {

    val doc = OpenAPIDocsInterpreter().toOpenAPI(
      GameStateEndpoints.endpoints :++
        PlayerEndpoints.endpoints :++
        EventEndpoints.endpoints :++
        ClubEndpoints.endpoints,
      "FOOTBALL_STOCK",
      "v1"
    )

    Http4sServerInterpreter[IO]().toRoutes(
      SwaggerUI[IO](
        doc.toYaml,
        SwaggerUIOptions.default.pathPrefix(List("swagger"))
      )
    )

  }

}
