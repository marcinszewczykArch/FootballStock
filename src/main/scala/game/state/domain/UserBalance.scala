package game.state.domain

import game.player.service.domain.PlayerProfile

import java.time.Instant

final case class UserBalance(
  user: User,
  portfolio: List[(PlayerProfile, BalancePerPlayer)],
  playersCurrentValue: BigDecimal,
  cash: BigDecimal,
  profit: BigDecimal,
  revenuePercent: Int,
  updatedAt: Instant
)

final case class BalancePerPlayer(
  shares: Int, //todo: with history
  averageBuyPrice: BigDecimal,
  totalBuyValue: BigDecimal,
  currentPrice: BigDecimal,
  totalCurrentValue: BigDecimal,
  profit: BigDecimal,
  revenuePercent: Int
)
