package game.modules.event

import game.modules.player.service.domain.PlayerId
import game.modules.state.domain.User
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

import java.time.Instant

sealed abstract class Event(user: User, timestamp: Instant, eventName: String, playerId: Option[PlayerId]) {
  def getUser: User         = user
  def getTimestamp: Instant = timestamp
  def getEventName          = eventName
  def getPlayerId           = playerId
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
  ) extends Event(user, timestamp, "SELL PLAYER", Some(playerId))

  final case class BuyPlayerEvent(
    playerId: PlayerId,
    playerName: String,
    shares: Int,
    value: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp, "BUY PLAYER", Some(playerId))

  final case class InitializeGameEvent(
    value: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp, "INITIALIZE GAME", None)

  final case class PlayerValueChanged(
    playerId: PlayerId,
    playerName: String,
    previousValue: BigDecimal,
    newValue: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp, "PLAYER VALUE CHANGED", Some(playerId))

  final case class PlayersUpdateEvent(
    updateSuccess: List[PlayerId],
    updateFailure: List[PlayerId],
    taskDurationSeconds: Int,
    timestamp: Instant
  ) extends Event(User(SYSTEM_USER_NAME), timestamp, "PLAYERS UPDATE", None)

  final case class UserDividendPayedEvent(
                                           user: User,
                                           timestamp: Instant,
                                           messages: List[String]
                                         ) extends Event(user, timestamp, "DIVIDEND PAYED", None)

}
