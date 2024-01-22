package http.event

import game.events.Event
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

object domain {

  case class EventsResponse(events: List[Event])

  object EventsResponse {
    implicit val eventsResponseDecoder: Decoder[EventsResponse] = deriveDecoder
    implicit val eventsResponseEncoder: Encoder[EventsResponse] = deriveEncoder
  }

}
