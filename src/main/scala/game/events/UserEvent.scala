package game.events

import game.player.service.domain.PlayerId
import java.time.Instant

sealed trait UserEvent {
  def user: String
  def transactionType: UserEventType
  def value: BigDecimal
  def timestamp: Instant
}

case class SellPlayerEvent(
  playerId: PlayerId,
  shares: Int,
  override val user: String,
  override val value: BigDecimal,
  override val timestamp: Instant
) extends UserEvent {
  override def transactionType: UserEventType = UserEventType.Sell
}

case class BuyPlayerEvent(
  playerId: PlayerId,
  shares: Int,
  override val user: String,
  override val value: BigDecimal,
  override val timestamp: Instant
) extends UserEvent {
  override def transactionType: UserEventType = UserEventType.Buy
}

case class InitializeGameEvent(
  override val user: String,
  override val value: BigDecimal,
  override val timestamp: Instant
) extends UserEvent {
  override def transactionType: UserEventType = UserEventType.InitializeGame
}
