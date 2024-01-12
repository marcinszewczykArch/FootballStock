package game.gameState

import game.player.service.domain.{PlayerId, PlayerProfile}

import java.time.Instant

final case class UserBalance(
  portfolio: List[(PlayerProfile, BalancePerPlayer)],
  playersCurrentValue: BigDecimal,
  cash: BigDecimal,
  profit: BigDecimal,
  revenuePercent: Int,
  updatedAt: Instant
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
