package http.player

import game.player.service.domain.{PlayerProfile, PlayerSimple}
import game.state.domain.UserBalance
import http.gameState.domain.UserGameStateResponse
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.{Instant, Year}

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

    def fromDomainPlayerProfile(playerProfile: PlayerProfile): PlayerProfileResponse = ???

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
                                   nationality: String,
                                   marketValue: String
                                 )

  object PlayerSimpleResponse {

    def fromDomainPlayerSimple(playerSimple: PlayerSimple): PlayerSimpleResponse = ???

    implicit val userGameStateResponseDecoder: Decoder[PlayerSimpleResponse] = deriveDecoder
    implicit val userGameStateResponseEncoder: Encoder[PlayerSimpleResponse] = deriveEncoder
  }

}
