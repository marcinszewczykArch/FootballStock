package http.event

import http.BaseEndpoint.baseEndpoint
import http.event.domain.EventsResponse
import http.security.SecuredEndpoints.AppEndpointSecretWithError
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

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
