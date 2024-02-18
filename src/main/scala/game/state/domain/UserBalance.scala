package game.state.domain

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.GameException
import game.player.service.PlayerService
import game.player.service.domain.PlayerId
import game.player.service.domain.PlayerProfile
import game.player.service.domain.PlayerStats
import utils.Type.ErrorOr

import java.time.Instant

final case class UserBalance(
  user: User,
  portfolio: List[(PlayerProfile, BalancePerPlayer)],
  wishlist: List[(PlayerProfile, Instant)],
  playersCurrentValue: BigDecimal,
  cash: BigDecimal,
  profit: BigDecimal,
  revenuePercent: Int,
  updatedAt: Instant
)

final case class BalancePerPlayer(
  sharesTotal: Int,
  averageBuyPrice: BigDecimal,
  totalBuyValue: BigDecimal,
  currentPrice: BigDecimal,
  totalCurrentValue: BigDecimal,
  profit: BigDecimal,
  revenuePercent: Int,
  lastPlayerMinutesPlayed: Int, //todo: to be moved to SharesBalance
  shares: List[SharesBalance],
  totalDividend: BigDecimal
)

final case class SharesBalance(
  number: Int,
  buyPrice: BigDecimal,
  totalBuyValue: BigDecimal,
  totalCurrentValue: BigDecimal,
  profit: BigDecimal,
  revenuePercent: Int,
  buyTimestamp: Instant,
  minutesPlayedSinceBuy: Int,
  minutesPlayedLastSeen: Int,
  dividend: BigDecimal
)

object UserBalance {
  import cats.implicits.toTraverseOps

  def fromUserState[F[_]: Sync](playerService: PlayerService[F])(userState: UserGameState)(user: User): F[ErrorOr[UserBalance]] = (for {
    portfolio <- EitherT(toBalancePortfolio(userState.portfolio)(playerService))
    wishlist  <- EitherT(toBalanceWishlist(userState.wishlist)(playerService))
    playersCurrentValue = portfolio.map { case (_, player) => player.totalCurrentValue }.sum
    cash                = userState.money
    initialCash         = UserGameState.initialCash //todo: initial cash should be taken from the 1st event of user
    profit              = playersCurrentValue + cash - initialCash
    revenuePercent      = ((profit / initialCash) * 100).toInt
    updatedAt           = userState.updatedAt
  } yield UserBalance(user, portfolio, wishlist, playersCurrentValue, cash, profit, revenuePercent, updatedAt)).value

  private def toBalancePortfolio[F[_]: Sync](
    portfolio: List[StockInfo]
  )(
    playerService: PlayerService[F]
  ): F[ErrorOr[List[(PlayerProfile, BalancePerPlayer)]]] =
    portfolio
      .traverse(stockInfo =>
        for {
          playerProfile <- EitherT(playerService.getPlayerProfileById(stockInfo.playerId))
          playerStats   <- EitherT(playerService.getPlayerStatsById(stockInfo.playerId))
          currentPrice     = playerProfile.marketValue
          sharesNumber     = stockInfo.shares.map(_.number).sum
          totalBuyValue    = stockInfo.shares.map { case Shares(number, buyPrice, _, _, _, _) => number * buyPrice / 100 }.sum
          currentValue     = (currentPrice * sharesNumber) / 100
          balancePerPlayer = BalancePerPlayer(
                               sharesTotal = sharesNumber,
                               averageBuyPrice = (totalBuyValue / sharesNumber) * 100,
                               totalBuyValue = totalBuyValue,
                               currentPrice = currentPrice,
                               totalCurrentValue = currentValue,
                               profit = currentValue - totalBuyValue,
                               revenuePercent = toRevenuePercent(totalBuyValue, currentValue),
                               lastPlayerMinutesPlayed = stockInfo.lastPlayerMinutesPlayed,
                               shares = toSharesBalance(stockInfo.shares, playerStats, currentPrice),
                               totalDividend = stockInfo.shares.map(_.dividend).sum
                             )
        } yield (playerProfile, balancePerPlayer)
      )
      .value

  private def toBalanceWishlist[F[_]: Sync](
    wishlist: List[(PlayerId, Instant)]
  )(
    playerService: PlayerService[F]
  ): F[ErrorOr[List[(PlayerProfile, Instant)]]] =
    wishlist
      .traverse { case (playerId, addedDate) =>
        playerService
          .getPlayerProfileById(playerId)
          .map(_.map(playerProfile => (playerProfile, addedDate)))
      }
      .map(_.sequence)

  private def toSharesBalance(shares: List[Shares], playerStats: PlayerStats, currentPrice: BigDecimal) =
    shares.map {
      case Shares(
            number,
            buyPrice,
            buyTimestamp,
            buyMinutesPlayed,
            minutesPlayedLastSeen,
            dividend
          ) =>
        val totalBuyValue         = (number * buyPrice) / 100
        val totalCurrentValue     = (currentPrice * number) / 100
        val totalMinutesPlayed    = playerStats.totalMinutesPlayed
        val minutesPlayedSinceBuy = totalMinutesPlayed - buyMinutesPlayed
        SharesBalance(
          number = number,
          buyPrice = buyPrice,
          totalBuyValue = totalBuyValue,
          totalCurrentValue = totalCurrentValue,
          profit = totalCurrentValue - totalBuyValue,
          revenuePercent = toRevenuePercent(totalBuyValue, totalCurrentValue),
          buyTimestamp = buyTimestamp,
          minutesPlayedSinceBuy = minutesPlayedSinceBuy,
          minutesPlayedLastSeen = minutesPlayedLastSeen,
          dividend = dividend
        )
    }

  private def toRevenuePercent(buyValue: BigDecimal, currentValue: BigDecimal): Int =
    buyValue match {
      case value if value == 0 => 0
      case _                   => ((currentValue - buyValue) * 100 / buyValue).toInt
    }

}
