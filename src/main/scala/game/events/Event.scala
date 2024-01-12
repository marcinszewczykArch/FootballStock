package game.events

import game.gameState.User
import game.player.service.domain.PlayerId
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

import java.time.Instant

sealed abstract class Event(user: User, timestamp: Instant) {
  def getUser: User = user
  def getTimestamp: Instant = timestamp
  def getEventName: String
}

object Event {
  implicit val eventDecoder: Decoder[Event] = deriveDecoder
  implicit val eventEncoder: Encoder[Event] = deriveEncoder

  final case class SellPlayerEvent(
    playerId: PlayerId,
    shares: Int,
    value: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp) {
    override def getEventName: String = "SELL_PLAYER"
  }

  final case class BuyPlayerEvent(
    playerId: PlayerId,
    shares: Int,
    value: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp) {
    override def getEventName: String = "BUY_PLAYER"
  }

  final case class InitializeGameEvent(
    value: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp) {
    override def getEventName: String = "INITIALIZE_GAME"
  }

  final case class PlayerValueChanged( //todo: add stream to send this user event periodically
    playerId: PlayerId,
    previousValue: BigDecimal,
    newValue: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp) {
    override def getEventName: String = "PLAYER_VALUE_CHANGED"
  }

  final case class PlayersUpdateEvent(
    updateSuccess: List[PlayerId],
    updateFailure: List[PlayerId],
    taskDurationSeconds: Int,
    timestamp: Instant
  ) extends Event(User("SYSTEM"), timestamp) {
    override def getEventName: String = "PLAYERS_UPDATE"
  }

}
