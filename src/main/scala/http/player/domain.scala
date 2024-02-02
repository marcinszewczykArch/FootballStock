package http.player

import game.player.service.domain.{MarketValue, MarketValueHistory, PlayerProfile, PlayerSimple}
import game.state.domain.UserBalance
import http.gameState.domain.UserGameStateResponse
import http.player.domain.MarketValueResponse.fromDomainMarketValue
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import utils.CurrencyFormatter

import java.time.Instant
import java.time.Year

object domain {

  final case class PlayerProfileResponse(
    id: Int,
    url: String,
    name: String,
    description: String,
    imageURL: String,
    dateOfBirth: String,
    citizenship: List[String],
    isRetired: Boolean,
    mainPosition: String,
    otherPositions: List[String],
    club: String,
    marketValue: String,
    updatedAt: String
  )

  object PlayerProfileResponse {

    def fromDomainPlayerProfile(playerProfile: PlayerProfile): PlayerProfileResponse = new PlayerProfileResponse(
      id = playerProfile.id.value,
      url = playerProfile.url,
      name = playerProfile.name,
      description = playerProfile.description,
      imageURL = playerProfile.imageURL,
      dateOfBirth = playerProfile.dateOfBirth,
      citizenship = playerProfile.citizenship,
      isRetired = playerProfile.isRetired,
      mainPosition = playerProfile.position.main,
      otherPositions = playerProfile.position.other,
      club = playerProfile.club,
      marketValue = CurrencyFormatter.toEuroString(playerProfile.marketValue),
      updatedAt = playerProfile.updatedAt.toString
    )

    implicit val userGameStateResponseDecoder: Decoder[PlayerProfileResponse] = deriveDecoder
    implicit val userGameStateResponseEncoder: Encoder[PlayerProfileResponse] = deriveEncoder
  }

  case class PlayerSearchResponse(players: List[PlayerSimpleResponse])

  object PlayerSearchResponse {

    def fromDomainPlayerSimpleList(players: List[PlayerSimple]): PlayerSearchResponse = PlayerSearchResponse(
      players
        .map(PlayerSimpleResponse.fromDomainPlayerSimple)
    )

    implicit val userGameStateResponseDecoder: Decoder[PlayerSearchResponse] = deriveDecoder
    implicit val userGameStateResponseEncoder: Encoder[PlayerSearchResponse] = deriveEncoder
  }

  case class PlayerSimpleResponse(
    id: Int,
    name: String,
    position: String,
    club: String,
    age: String,
    nationalities: List[String],
    marketValue: String
  )

  object PlayerSimpleResponse {

    def fromDomainPlayerSimple(playerSimple: PlayerSimple): PlayerSimpleResponse = new PlayerSimpleResponse(
      id = playerSimple.id.value,
      name = playerSimple.name,
      position = playerSimple.position,
      club = playerSimple.club,
      age = playerSimple.age,
      nationalities = playerSimple.nationalities,
      marketValue = CurrencyFormatter.toEuroString(playerSimple.marketValue)
    )

    implicit val userGameStateResponseDecoder: Decoder[PlayerSimpleResponse] = deriveDecoder
    implicit val userGameStateResponseEncoder: Encoder[PlayerSimpleResponse] = deriveEncoder
  }

  case class MarketValueHistoryResponse(
                                        id: Int,
                                        marketValue: Int,
                                        marketValueHistory: List[MarketValueResponse],
                                        updatedAt: String
                                      )

  object MarketValueHistoryResponse {

    def fromDomainMarketValueHistory(marketValueHistory: MarketValueHistory): MarketValueHistoryResponse = new MarketValueHistoryResponse(
      id = marketValueHistory.id.value,
      marketValue = marketValueHistory.marketValue.toInt,
      marketValueHistory = marketValueHistory.marketValueHistory.map(fromDomainMarketValue),
      updatedAt = marketValueHistory.updatedAt.toString,
    )

    implicit val marketValueHistoryResponseDecoder: Decoder[MarketValueHistoryResponse] = deriveDecoder
    implicit val marketValueHistoryResponseEncoder: Encoder[MarketValueHistoryResponse] = deriveEncoder
  }

  case class MarketValueResponse(
                                  age: Int,
                                  date: Instant,
                                  clubName: String,
                                  value: Int,
                                  clubId: Int
  )

  object MarketValueResponse {

    def fromDomainMarketValue(marketValue: MarketValue): MarketValueResponse = new MarketValueResponse(
      age = marketValue.age,
      date = marketValue.date,
      clubName = marketValue.clubName,
      value = marketValue.value.toInt,
      clubId = marketValue.clubId
    )

    implicit val marketValueResponseDecoder: Decoder[MarketValueResponse] = deriveDecoder
    implicit val marketValueResponseEncoder: Encoder[MarketValueResponse] = deriveEncoder
  }


}
