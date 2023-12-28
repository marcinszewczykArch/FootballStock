package multiplayer.domain

import services.domain.PlayerId
import utils.TimeProvider

import java.time.Instant

final case class UserBalance(
  portfolio: Map[PlayerId, BalancePerPlayer],
  playersCurrentValue: BigDecimal,
  cash: BigDecimal,
  profit: BigDecimal,
  revenuePercent: Int
)

final case class BalancePerPlayer(
  shares: Int,
  averageBuyPrice: BigDecimal,
  totalBuyValue: BigDecimal,
  currentPrice: BigDecimal,
  totalCurrentValue: BigDecimal,
  profit: BigDecimal,
  revenuePercent: Int
)
