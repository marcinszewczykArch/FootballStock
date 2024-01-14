package http.gameState

import game.state.domain.{BalancePerPlayer, Shares, User, UserBalance}
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

  final case class BuyPlayerRequest(user: String, playerId: Int, sharesToBuy: Int)

  object BuyPlayerRequest {
    implicit val buyPlayerRequestDecoder: Decoder[BuyPlayerRequest] = deriveDecoder
    implicit val buyPlayerRequestEncoder: Encoder[BuyPlayerRequest] = deriveEncoder
  }

  final case class BuyPlayerResponse(message: String)

  object BuyPlayerResponse {
    implicit val buyPlayerResponseDecoder: Decoder[BuyPlayerResponse] = deriveDecoder
    implicit val buyPlayerResponseEncoder: Encoder[BuyPlayerResponse] = deriveEncoder
  }

  final case class SellPlayerRequest(user: String, playerId: Int, sharesToSell: Int)

  object SellPlayerRequest {
    implicit val sellPlayerRequestDecoder: Decoder[SellPlayerRequest] = deriveDecoder
    implicit val sellPlayerRequestEncoder: Encoder[SellPlayerRequest] = deriveEncoder
  }

  final case class SellPlayerResponse(message: String)

  object SellPlayerResponse {
    implicit val sellPlayerResponseDecoder: Decoder[SellPlayerResponse] = deriveDecoder
    implicit val sellPlayerResponseEncoder: Encoder[SellPlayerResponse] = deriveEncoder
  }

  final case class CreateNewUserResponse(message: String)

  object CreateNewUserResponse {
    implicit val sellPlayerResponseDecoder: Decoder[CreateNewUserResponse] = deriveDecoder
    implicit val sellPlayerResponseEncoder: Encoder[CreateNewUserResponse] = deriveEncoder
  }
}
