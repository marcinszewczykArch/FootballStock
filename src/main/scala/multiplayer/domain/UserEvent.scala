package multiplayer.domain

import services.domain.PlayerId

import java.time.Instant

sealed abstract class UserEvent(
                                 transactionType: UserEventType,
                                 value: BigDecimal,
                                 timestamp: Instant
)

case class SellPlayerEvent(
  playerId: PlayerId,
  shares: Int,
  value: BigDecimal,
  timestamp: Instant
) extends UserEvent(UserEventType.Sell, value, timestamp)

case class BuyPlayerEvent(
  playerId: PlayerId,
  shares: Int,
  value: BigDecimal,
  timestamp: Instant
) extends UserEvent(UserEventType.Buy, value, timestamp)

case class
InitializeGameEvent(
  startBudget: BigDecimal,
  timestamp: Instant
) extends UserEvent(UserEventType.InitializeGame, startBudget, timestamp)
