package game.events

import game.gameState.User
import game.player.service.domain.PlayerId
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant

sealed abstract class Event(user: User, timestamp: Instant){
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

//  implicit val sellPlayerEventDecoder: Decoder[SellPlayerEvent] = deriveDecoder
//  implicit val sellPlayerEventEncoder: Encoder[SellPlayerEvent] = deriveEncoder

  final case class BuyPlayerEvent(
    playerId: PlayerId,
    shares: Int,
    value: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp) {
    override def getEventName: String = "BUY_PLAYER"
  }

//  implicit val buyPlayerEventDecoder: Decoder[BuyPlayerEvent] = deriveDecoder
//  implicit val buyPlayerEventEncoder: Encoder[BuyPlayerEvent] = deriveEncoder

  final case class InitializeGameEvent(
    value: BigDecimal,
    user: User,
    timestamp: Instant
  ) extends Event(user, timestamp) {
    override def getEventName: String = "INITIALIZE_GAME"
  }

//  implicit val initializeGameEventDecoder: Decoder[InitializeGameEvent] = deriveDecoder
//  implicit val initializeGameEventEncoder: Encoder[InitializeGameEvent] = deriveEncoder

  final case class PlayersUpdateEvent(
    updateSuccess: List[PlayerId],
    updateFailure: List[PlayerId],
    taskDurationSeconds: Int,
    timestamp: Instant
  ) extends Event(User("SYSTEM"), timestamp) {
    override def getEventName: String = "PLAYERS_UPDATE"
  }

//  implicit val playersUpdateEventDecoder: Decoder[PlayersUpdateEvent] = deriveDecoder
//  implicit val playersUpdateEventEncoder: Encoder[PlayersUpdateEvent] = deriveEncoder

}
