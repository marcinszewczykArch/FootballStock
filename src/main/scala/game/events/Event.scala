package game.events

import game.state.domain.User
import game.player.service.domain.PlayerId
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

import java.time.Instant

sealed abstract class Event(user: User, timestamp: Instant, eventName: String) {
  def getUser: User         = user
  def getTimestamp: Instant = timestamp
  def getEventName          = eventName
}

object Event {
  val SYSTEM_USER_NAME                      = "SYSTEM"
  implicit val eventDecoder: Decoder[Event] = deriveDecoder
  implicit val eventEncoder: Encoder[Event] = deriveEncoder

  final case class SellPlayerEvent(
    playerId: PlayerId,
    playerName: String,
    shares: Int,
    value: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp, "SELL_PLAYER")

  final case class BuyPlayerEvent(
    playerId: PlayerId,
    playerName: String,
    shares: Int,
    value: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp, "BUY_PLAYER")

  final case class InitializeGameEvent(
    value: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp, "INITIALIZE_GAME")

  final case class PlayerValueChanged( //todo: add stream to send this user event periodically
    playerId: PlayerId,
    playerName: String,
    previousValue: BigDecimal,
    newValue: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp, "PLAYER_VALUE_CHANGED")

  final case class PlayersUpdateEvent(
    updateSuccess: List[PlayerId],
    updateFailure: List[PlayerId],
    taskDurationSeconds: Int,
    timestamp: Instant
  ) extends Event(User(SYSTEM_USER_NAME), timestamp, "PLAYERS_UPDATE")

}
