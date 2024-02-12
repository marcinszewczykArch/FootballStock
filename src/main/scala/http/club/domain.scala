package http.club

import game.club.service.domain.{ClubPlayer, ClubProfile, ClubSimple}
import game.player.service.domain.MarketValue
import game.player.service.domain.MarketValueHistory
import game.player.service.domain.PlayerProfile
import game.player.service.domain.PlayerSimple
import http.player.domain.MarketValueResponse.fromDomainMarketValue
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import utils.CurrencyFormatter

import java.time.Instant

object domain {

  final case class ClubProfileResponse(
    id: Int,
    name: String
  )

  object ClubProfileResponse {

    def fromDomainClubProfile(clubProfile: ClubProfile): ClubProfileResponse = new ClubProfileResponse(
      id = clubProfile.id.value,
      name = clubProfile.name
    )

    implicit val clubProfileResponseDecoder: Decoder[ClubProfileResponse] = deriveDecoder
    implicit val clubProfileResponseEncoder: Encoder[ClubProfileResponse] = deriveEncoder
  }

  final case class ClubSearchResponse(clubs: List[ClubSimpleResponse])

  object ClubSearchResponse {

    def fromDomainClubSimpleList(clubs: List[ClubSimple]): ClubSearchResponse = ClubSearchResponse(
      clubs
        .map(ClubSimpleResponse.fromDomainClubSimple)
    )

    implicit val clubSearchResponseDecoder: Decoder[ClubSearchResponse] = deriveDecoder
    implicit val clubSearchResponseEncoder: Encoder[ClubSearchResponse] = deriveEncoder
  }

  final case class ClubSimpleResponse(
    id: Int,
    name: String
  )

  object ClubSimpleResponse {

    def fromDomainClubSimple(clubSimple: ClubSimple): ClubSimpleResponse = new ClubSimpleResponse(
      id = clubSimple.id.value,
      name = clubSimple.name
    )

    implicit val clubSimpleResponseResponseDecoder: Decoder[ClubSimpleResponse] = deriveDecoder
    implicit val clubSimpleResponseResponseEncoder: Encoder[ClubSimpleResponse] = deriveEncoder
  }

  final case class ClubPlayersResponse(
    players: List[ClubPlayerResponse]
  )

  object ClubPlayersResponse {

    def fromDomainClubPlayerList(clubs: List[ClubPlayer]): ClubPlayersResponse = ClubPlayersResponse(
      clubs
        .map(ClubPlayerResponse.fromDomainClubPlayer)
    )

    implicit val clubPlayersResponseResponseDecoder: Decoder[ClubPlayersResponse] = deriveDecoder
    implicit val clubPlayersResponseResponseEncoder: Encoder[ClubPlayersResponse] = deriveEncoder
  }

  final case class ClubPlayerResponse(
    id: Int,
    name: String,
    position: String,
    dateOfBirth: String,
    age: Int,
    nationality: List[String],
    height: String,
    foot: String,
    joinedOn: String,
    joined: String,
    signedFrom: String,
    contract: String,
    marketValue: String
  )

  object ClubPlayerResponse {

    def fromDomainClubPlayer(clubPlayer: ClubPlayer): ClubPlayerResponse = new ClubPlayerResponse(
      id = clubPlayer.id.value,
      name = clubPlayer.name,
      position = clubPlayer.position,
      dateOfBirth = clubPlayer.dateOfBirth,
      age = clubPlayer.age,
      nationality = clubPlayer.nationality,
      height = clubPlayer.height,
      foot = clubPlayer.foot,
      joinedOn = clubPlayer.joinedOn,
      joined = clubPlayer.joined,
      signedFrom = clubPlayer.signedFrom,
      contract = clubPlayer.contract,
      marketValue = CurrencyFormatter.toEuroString(clubPlayer.marketValue)
    )

    implicit val clubPlayerResponseResponseDecoder: Decoder[ClubPlayerResponse] = deriveDecoder
    implicit val clubPlayerResponseResponseEncoder: Encoder[ClubPlayerResponse] = deriveEncoder
  }

}
