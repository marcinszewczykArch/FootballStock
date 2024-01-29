package http.event

import cats.effect.Sync
import game.events.Event
import game.events.Event._
import game.logic.GameEngine
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import org.typelevel.log4cats.LoggerFactory
import utils.CurrencyFormatter

import java.time.Instant

object domain {

  def toEventsResponse(events: List[Event]): EventsResponse = EventsResponse(
    events
      .map { event =>
        EventResponse(
          eventName = event.getEventName,
          user = event.getUser.value,
          timestamp = event.getTimestamp,
          message = event match {
            case SellPlayerEvent(playerId, playerName, shares, value, _, _)               =>
              s"$playerName [${playerId.value}] - $shares sold for ${CurrencyFormatter.toEuroString(value)}"
            case BuyPlayerEvent(playerId, playerName, shares, value, _, _)                =>
              s"$playerName [${playerId.value}] - $shares bought for ${CurrencyFormatter.toEuroString(value)}"
            case InitializeGameEvent(_, _, _)                                             =>
              "game initialized"
            case PlayerValueChanged(playerId, playerName, previousValue, newValue, _, _)  =>
              s"$playerName [${playerId.value}] - player value changed. Previous value: ${CurrencyFormatter.toEuroString(previousValue)}, " +
                s"new value: ${CurrencyFormatter.toEuroString(newValue)}"
            case PlayersUpdateEvent(updateSuccess, updateFailure, taskDurationSeconds, _) =>
              s"Task Duration: $taskDurationSeconds. " +
                s"Players updated with success: $updateSuccess, Players not updated because of failure: $updateFailure"
          }
        )

      }
      .sortBy(_.timestamp)
      .reverse
  )

  case class EventsResponse(events: List[EventResponse])

  case class EventResponse(
    eventName: String,
    user: String,
    timestamp: Instant,
    message: String
  )

  object EventsResponse {
    implicit val eventsResponseDecoder: Decoder[EventsResponse] = deriveDecoder
    implicit val eventsResponseEncoder: Encoder[EventsResponse] = deriveEncoder
  }

  object EventResponse {
    implicit val eventsResponseDecoder: Decoder[EventResponse] = deriveDecoder
    implicit val eventsResponseEncoder: Encoder[EventResponse] = deriveEncoder
  }

}
