package http.gameState

import game.gameState.domain.BalancePerPlayer
import game.gameState.domain.Shares
import game.gameState.domain.UserBalance
import game.player.service.domain.PlayerId
import game.player.service.domain.PlayerProfile
import game.player.service.domain.PlayerSimple
import http.gameState.domain.PlayerStockResponse
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

import java.time.Instant
import java.time.Year

object domain {

  final case class UserGameStateResponse(
    portfolio: List[PlayerStockResponse],
    playersCurrentValue: BigDecimal,
    cash: BigDecimal,
    profit: BigDecimal,
    revenuePercent: Int,
    updatedAt: Instant
  )

  final case class PlayerStockResponse(
    id: Int,
    name: String,
    position: List[String],
    club: String,
    age: Int,
    citizenship: List[String],
    marketValue: BigDecimal,
    shares: Int,
    averageBuyPrice: BigDecimal,
    totalBuyValue: BigDecimal,
    currentPrice: BigDecimal,
    totalCurrentValue: BigDecimal,
    profit: BigDecimal,
    revenuePercent: Int
  )

  object UserGameStateResponse {

    def fromUserBalance(userBalance: UserBalance): UserGameStateResponse = userBalance match {
      case UserBalance(portfolio, playersCurrentValue, cash, profit, revenuePercent, updatedAt) =>
        new UserGameStateResponse(
          portfolio = portfolio.map { case (playerProfile, balancePerPlayer) =>
            PlayerStockResponse.fromDomainPortfolio(playerProfile, balancePerPlayer)
          },
          playersCurrentValue,
          cash,
          profit,
          revenuePercent,
          updatedAt
        )
    }

    implicit val userGameStateResponseDecoder: Decoder[UserGameStateResponse] = deriveDecoder
    implicit val userGameStateResponseEncoder: Encoder[UserGameStateResponse] = deriveEncoder
  }

  object PlayerStockResponse {

    def fromDomainPortfolio(playerProfile: PlayerProfile, balancePerPlayer: BalancePerPlayer): PlayerStockResponse =
      (playerProfile, balancePerPlayer) match {
        case (
              PlayerProfile(
                id,
                url,
                name,
                description,
                imageURL,
                dateOfBirth,
                citizenship,
                isRetired,
                position,
                club,
                marketValue,
                updatedAt
              ),
              BalancePerPlayer(shares, averageBuyPrice, totalBuyValue, currentPrice, totalCurrentValue, profit, revenuePercent)
            ) =>
          new PlayerStockResponse(
            id = id.value,
            name = name,
            position = position.main +: position.other,
            club = club,
            age = ageFromDateOfBirth(dateOfBirth),
            citizenship = citizenship,
            marketValue = marketValue,
            shares = shares,
            averageBuyPrice = averageBuyPrice,
            totalBuyValue = totalBuyValue,
            currentPrice = currentPrice,
            totalCurrentValue = totalCurrentValue,
            profit = profit,
            revenuePercent = revenuePercent
          )
      }

    def ageFromDateOfBirth(dateOfBirth: String): Int = { //todo: improve me
      val birthYear   = dateOfBirth.takeRight(4).toIntOption
      val currentYear = Year.now.getValue
      birthYear.map(currentYear - _).getOrElse(0)
    }

    implicit val playerStockResponseDecoder: Decoder[PlayerStockResponse] = deriveDecoder
    implicit val playerStockResponseEncoder: Encoder[PlayerStockResponse] = deriveEncoder
  }

}
