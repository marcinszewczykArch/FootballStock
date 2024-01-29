package http.player

import game.player.service.domain.PlayerProfile
import game.player.service.domain.PlayerSimple
import game.state.domain.UserBalance
import http.gameState.domain.UserGameStateResponse
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

  case class PlayerSearchResponse(players: List[PlayerSimpleResponse])

  case class PlayerSimpleResponse(
    id: Int,
    name: String,
    position: String,
    club: String,
    age: String,
    nationalities: List[String],
    marketValue: String
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

  object PlayerSearchResponse {

    def fromDomainPlayerSimpleList(players: List[PlayerSimple]): PlayerSearchResponse = PlayerSearchResponse(
      players
        .map(PlayerSimpleResponse.fromDomainPlayerSimple)
    )

    implicit val userGameStateResponseDecoder: Decoder[PlayerSearchResponse] = deriveDecoder
    implicit val userGameStateResponseEncoder: Encoder[PlayerSearchResponse] = deriveEncoder
  }

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

}
