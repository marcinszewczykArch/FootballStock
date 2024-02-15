package http.state

import game.state.domain.BalancePerPlayer
import game.state.domain.Shares
import game.state.domain.User
import game.state.domain.UserBalance
import game.player.service.domain.PlayerId
import game.player.service.domain.PlayerProfile
import game.player.service.domain.PlayerSimple
import http.state.domain.PlayerStockResponse
import http.state.domain.PlayerStockResponse.ageFromDateOfBirth
import http.state.domain.WishlistPlayerResponse.fromDomainWishlist
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import utils.CurrencyFormatter

import java.time.Instant
import java.time.Year

object domain {

  final case class UserGameStateResponse(
    user: String,
    portfolio: List[PlayerStockResponse],
    wishlist: List[WishlistPlayerResponse],
    playersCurrentValue: Int,
    cash: Int,
    profit: Int,
    revenuePercent: Int,
    updatedAt: Instant
  )

  final case class PlayerStockResponse(
    id: Int,
    name: String,
    position: List[String],
    clubId: Int,
    club: String,
    age: Int,
    citizenship: List[String],
    marketValue: String,
    imageURL: String,
    description: String,
    shares: Int,
    averageBuyPrice: String,
    totalBuyValue: String,
    currentPrice: String,
    totalCurrentValue: String,
    profit: String,
    revenuePercent: Int
  )

  final case class WishlistPlayerResponse(
    id: Int,
    name: String,
    position: List[String],
    club: String,
    clubId: Int,
    age: Int,
    nationalities: List[String],
    marketValue: String,
    isRetired: Boolean,
    addedDate: Instant
  )

  object WishlistPlayerResponse {

    def fromDomainWishlist(wishlist: List[(PlayerProfile, Instant)]): List[WishlistPlayerResponse] = wishlist.map {
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
              clubId,
              club,
              marketValue,
              updatedAt
            ),
            addedDate
          ) =>
        new WishlistPlayerResponse(
          id = id.value,
          name = name,
          position = position.main +: position.other,
          club = club,
          clubId = clubId,
          age = ageFromDateOfBirth(dateOfBirth),
          nationalities = citizenship,
          marketValue = CurrencyFormatter.toEuroString(marketValue),
          isRetired = isRetired,
          addedDate = addedDate
        )
    }

    implicit val wishlistPlayerResponseDecoder: Decoder[WishlistPlayerResponse] = deriveDecoder
    implicit val wishlistPlayerResponseEncoder: Encoder[WishlistPlayerResponse] = deriveEncoder

  }

  final case class BuyPlayerRequest(user: String, playerId: Int, sharesToBuy: Int)

  final case class BuyPlayerResponse(message: String)

  final case class SellPlayerRequest(user: String, playerId: Int, sharesToSell: Int)

  final case class SellPlayerResponse(message: String)

  final case class CreateNewUserResponse(message: String)

  object UserGameStateResponse {

    def fromUserBalance(userBalance: UserBalance): UserGameStateResponse = userBalance match {
      case UserBalance(user, portfolio, wishlist, playersCurrentValue, cash, profit, revenuePercent, updatedAt) =>
        new UserGameStateResponse(
          user = user.value,
          portfolio = portfolio.map { case (playerProfile, balancePerPlayer) =>
            PlayerStockResponse.fromDomainPortfolio(playerProfile, balancePerPlayer)
          },
          wishlist = fromDomainWishlist(wishlist),
          playersCurrentValue.toInt,
          cash.toInt,
          profit.toInt,
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
                clubId,
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
            clubId = clubId,
            club = club,
            age = ageFromDateOfBirth(dateOfBirth),
            citizenship = citizenship,
            marketValue = CurrencyFormatter.toEuroString(marketValue),
            imageURL = imageURL,
            description = description,
            shares = shares,
            averageBuyPrice = CurrencyFormatter.toEuroString(averageBuyPrice),
            totalBuyValue = CurrencyFormatter.toEuroString(totalBuyValue),
            currentPrice = CurrencyFormatter.toEuroString(currentPrice),
            totalCurrentValue = CurrencyFormatter.toEuroString(totalCurrentValue),
            profit = CurrencyFormatter.toEuroString(profit),
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

  object BuyPlayerRequest {
    implicit val buyPlayerRequestDecoder: Decoder[BuyPlayerRequest] = deriveDecoder
    implicit val buyPlayerRequestEncoder: Encoder[BuyPlayerRequest] = deriveEncoder
  }

  object BuyPlayerResponse {
    implicit val buyPlayerResponseDecoder: Decoder[BuyPlayerResponse] = deriveDecoder
    implicit val buyPlayerResponseEncoder: Encoder[BuyPlayerResponse] = deriveEncoder
  }

  object SellPlayerRequest {
    implicit val sellPlayerRequestDecoder: Decoder[SellPlayerRequest] = deriveDecoder
    implicit val sellPlayerRequestEncoder: Encoder[SellPlayerRequest] = deriveEncoder
  }

  object SellPlayerResponse {
    implicit val sellPlayerResponseDecoder: Decoder[SellPlayerResponse] = deriveDecoder
    implicit val sellPlayerResponseEncoder: Encoder[SellPlayerResponse] = deriveEncoder
  }

  object CreateNewUserResponse {
    implicit val sellPlayerResponseDecoder: Decoder[CreateNewUserResponse] = deriveDecoder
    implicit val sellPlayerResponseEncoder: Encoder[CreateNewUserResponse] = deriveEncoder
  }

}
