package http.event

import game.events.Event
import http.BaseEndpoint.baseEndpoint
import http.event.domain.EventsResponse
import http.player.domain.{PlayerProfileResponse, PlayerSearchResponse}
import http.security.SecuredEndpoints.AppEndpointSecretWithError
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

import java.time.Instant

object EventEndpoints {

  lazy val endpoints: List[AppEndpointSecretWithError[_, _]] = List(
    getEvents
  )

  lazy val getEvents: AppEndpointSecretWithError[String, EventsResponse] = baseEndpoint
    .get
    .in("events")
    .in(query[String]("user"))
    .summary("Get game events")
    .description("Get game events for given User")
    .tag("Events")
    .out(jsonBody[EventsResponse]) //todo: add pagination


}
