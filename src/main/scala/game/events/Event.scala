package game.events

import game.gameState.User
import game.player.service.domain.PlayerId

import java.time.Instant

sealed trait Event {
  def user: User
  def timestamp: Instant
}

case class SellPlayerEvent(
  playerId: PlayerId,
  shares: Int,
  value: BigDecimal,
  override val user: User,
  override val timestamp: Instant
) extends Event

case class BuyPlayerEvent(
  playerId: PlayerId,
  shares: Int,
  value: BigDecimal,
  override val user: User,
  override val timestamp: Instant
) extends Event

case class InitializeGameEvent(
  value: BigDecimal,
  override val user: User,
  override val timestamp: Instant
) extends Event

case class PlayersUpdateEvent(
  updateSuccess: List[PlayerId],
  updateFailure: List[PlayerId],
  taskDurationSeconds: Int,
  override val timestamp: Instant
) extends Event {
  override def user: User = User("SYSTEM")
}
