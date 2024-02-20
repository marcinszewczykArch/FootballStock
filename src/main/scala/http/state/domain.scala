package http.state

import game.modules.player.service.domain.PlayerProfile
import game.modules.state.domain.{BalancePerPlayer, UserBalance}
import http.state.domain.PlayerStockResponse.ageFromDateOfBirth
import http.state.domain.WishlistPlayerResponse.fromDomainWishlist
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import utils.CurrencyFormatter

import java.time.{Instant, Year}

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
    sharesTotal: Int,
    averageBuyPrice: String,
    totalBuyValue: String,
    currentPrice: String,
    totalCurrentValue: String,
    profit: String,
    revenuePercent: Int,
    lastPlayerMinutesPlayed: Int, //todo: this will be moved to shares
    shares: List[SharesResponse],
    totalDividend: String
  )

  final case class SharesResponse(
    number: Int,
    buyPrice: String,
    totalBuyValue: String,
    totalCurrentValue: String,
    profit: String,
    revenuePercent: Int,
    buyTimestamp: Instant,
    minutesPlayedSinceBuy: Int,
    minutesPlayedLastSeen: Int,
    dividend: String
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
    addedDate: Instant,
    imageURL: String
  )

  final case class BuyPlayerRequest(user: String, playerId: Int, sharesToBuy: Int)

  final case class BuyPlayerResponse(message: String)

  final case class SellPlayerRequest(user: String, playerId: Int, sharesToSell: Int)

  final case class SellPlayerResponse(message: String)

  final case class CreateNewUserResponse(message: String)

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
          addedDate = addedDate,
          imageURL = imageURL
        )
    }

    implicit val wishlistPlayerResponseDecoder: Decoder[WishlistPlayerResponse] = deriveDecoder
    implicit val wishlistPlayerResponseEncoder: Encoder[WishlistPlayerResponse] = deriveEncoder

  }

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
              BalancePerPlayer(
                sharesTotal,
                averageBuyPrice,
                totalBuyValue,
                currentPrice,
                totalCurrentValue,
                profit,
                revenuePercent,
                lastPlayerMinutesPlayed,
                shares,
                totalDividend
              )
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
            sharesTotal = sharesTotal,
            averageBuyPrice = CurrencyFormatter.toEuroString(averageBuyPrice),
            totalBuyValue = CurrencyFormatter.toEuroString(totalBuyValue),
            currentPrice = CurrencyFormatter.toEuroString(currentPrice),
            totalCurrentValue = CurrencyFormatter.toEuroString(totalCurrentValue),
            profit = CurrencyFormatter.toEuroString(profit),
            revenuePercent = revenuePercent,
            lastPlayerMinutesPlayed = lastPlayerMinutesPlayed,
            shares = shares.map(s =>
              SharesResponse(
                number = s.number,
                buyPrice = CurrencyFormatter.toEuroString(s.buyPrice),
                totalBuyValue = CurrencyFormatter.toEuroString(s.totalBuyValue),
                totalCurrentValue = CurrencyFormatter.toEuroString(s.totalCurrentValue),
                profit = CurrencyFormatter.toEuroString(s.profit),
                revenuePercent = s.revenuePercent,
                buyTimestamp = s.buyTimestamp,
                minutesPlayedSinceBuy = s.minutesPlayedSinceBuy,
                minutesPlayedLastSeen = s.minutesPlayedLastSeen,
                dividend = CurrencyFormatter.toEuroString(s.dividend)
              )
            ),
            totalDividend = CurrencyFormatter.toEuroString(totalDividend)
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

  object SharesResponse {
    implicit val sharesResponseDecoder: Decoder[SharesResponse] = deriveDecoder
    implicit val sharesResponseEncoder: Encoder[SharesResponse] = deriveEncoder
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
