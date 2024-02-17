package game.state.domain

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.player.service.PlayerService
import game.player.service.domain.PlayerProfile
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
    portfolio <- userState
                   .portfolio
                   .map { case playerId -> stockInfo =>
                     for {
                       playerProfile <- EitherT(playerService.getPlayerProfileById(playerId))
                       playerStats   <- EitherT(playerService.getPlayerStatsById(playerId))
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
                                            revenuePercent = totalBuyValue match {
                                              case value if value == 0 => 0
                                              case _                   => ((currentValue - totalBuyValue) * 100 / totalBuyValue).toInt
                                            },
                                            lastPlayerMinutesPlayed = stockInfo.lastPlayerMinutesPlayed,
                                            shares = stockInfo.shares.map {
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
                                                  revenuePercent = totalBuyValue match {
                                                    case value if value == 0 => 0
                                                    case _                   => ((totalCurrentValue - totalBuyValue) * 100 / totalBuyValue).toInt
                                                  },
                                                  buyTimestamp = buyTimestamp,
                                                  minutesPlayedSinceBuy = minutesPlayedSinceBuy,
                                                  minutesPlayedLastSeen = minutesPlayedLastSeen,
                                                  dividend = dividend
                                                )
                                            },
                                            totalDividend = stockInfo.shares.map(_.dividend).sum
                                          )
                     } yield (playerProfile, balancePerPlayer)
                   }
                   .toList
                   .sequence
    wishlist  <- EitherT(
                   userState
                     .wishlist
                     .traverse { case (playerId, addedDate) =>
                       playerService
                         .getPlayerProfileById(playerId)
                         .map(_.map(playerProfile => (playerProfile, addedDate)))
                     }
                     .map(_.sequence)
                 )
    playersCurrentValue = portfolio.map(_._2.totalCurrentValue).sum
    cash           = userState.money
    profit         = playersCurrentValue + cash - UserGameState.initialCash
    revenuePercent = ((profit / UserGameState.initialCash) * 100).toInt
    updatedAt      = userState.updatedAt
  } yield UserBalance(user, portfolio, wishlist, playersCurrentValue, cash, profit, revenuePercent, updatedAt)).value

}
