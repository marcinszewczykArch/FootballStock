package http.club

import game.modules.club.service.domain.ClubLeague
import game.modules.club.service.domain.ClubPlayer
import game.modules.club.service.domain.ClubPlayers
import game.modules.club.service.domain.ClubProfile
import game.modules.club.service.domain.ClubSimple
import game.modules.club.service.domain.ClubSquad
import http.club.domain.ClubLeagueResponse.leagueToLeagueResponse
import http.club.domain.ClubSquadResponse.squadToSquadResponse
import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import utils.CurrencyFormatter

import java.time.Instant

object domain {

  final case class ClubProfileResponse(
    id: Int,
    url: String,
    name: String,
    officialName: String,
    image: String,
    website: String,
    foundedOn: String,
    stadiumName: String,
    stadiumSeats: Int,
    currentMarketValue: String,
    squad: ClubSquadResponse,
    league: ClubLeagueResponse,
    updatedAt: Instant
  )

  final case class ClubSquadResponse(
    size: Int,
    averageAge: Double,
    foreigners: Int,
    nationalTeamPlayers: Int
  )

  final case class ClubLeagueResponse(
    id: String,
    name: String,
    countryID: Int,
    countryName: String,
    tier: String
  )

  final case class ClubSearchResponse(clubs: List[ClubSimpleResponse])

  final case class ClubSimpleResponse(
    id: Int,
    url: String,
    name: String,
    country: String,
    squad: Int,
    marketValue: String
  )

  final case class ClubPlayersResponse(
    id: Int,
    players: List[ClubPlayerResponse],
    updatedAt: Instant
  )

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

  object ClubProfileResponse {

    def fromDomainClubProfile(clubProfile: ClubProfile): ClubProfileResponse = new ClubProfileResponse(
      id = clubProfile.id.value,
      url = clubProfile.url,
      name = clubProfile.name,
      officialName = clubProfile.officialName,
      image = clubProfile.image,
      website = clubProfile.website,
      foundedOn = clubProfile.foundedOn,
      stadiumName = clubProfile.stadiumName,
      stadiumSeats = clubProfile.stadiumSeats,
      currentMarketValue = CurrencyFormatter.toEuroString(clubProfile.currentMarketValue),
      squad = squadToSquadResponse(clubProfile.squad),
      league = leagueToLeagueResponse(clubProfile.league),
      updatedAt = clubProfile.updatedAt
    )

    implicit val clubProfileResponseDecoder: Decoder[ClubProfileResponse] = deriveDecoder
    implicit val clubProfileResponseEncoder: Encoder[ClubProfileResponse] = deriveEncoder
  }

  object ClubSearchResponse {

    def fromDomainClubSimpleList(clubs: List[ClubSimple]): ClubSearchResponse = ClubSearchResponse(
      clubs
        .map(ClubSimpleResponse.fromDomainClubSimple)
    )

    implicit val clubSearchResponseDecoder: Decoder[ClubSearchResponse] = deriveDecoder
    implicit val clubSearchResponseEncoder: Encoder[ClubSearchResponse] = deriveEncoder
  }

  object ClubSimpleResponse {

    def fromDomainClubSimple(clubSimple: ClubSimple): ClubSimpleResponse = new ClubSimpleResponse(
      id = clubSimple.id.value,
      url = clubSimple.url,
      name = clubSimple.name,
      country = clubSimple.country,
      squad = clubSimple.squad,
      marketValue = clubSimple.marketValue
    )

    implicit val clubSimpleResponseResponseDecoder: Decoder[ClubSimpleResponse] = deriveDecoder
    implicit val clubSimpleResponseResponseEncoder: Encoder[ClubSimpleResponse] = deriveEncoder
  }

  object ClubPlayersResponse {

    def fromDomainClubPlayers(clubPlayers: ClubPlayers): ClubPlayersResponse = ClubPlayersResponse(
      id = clubPlayers.id.value,
      players = clubPlayers.players.map(ClubPlayerResponse.fromDomainClubPlayer),
      updatedAt = clubPlayers.updatedAt
    )

    implicit val clubPlayersResponseResponseDecoder: Decoder[ClubPlayersResponse] = deriveDecoder
    implicit val clubPlayersResponseResponseEncoder: Encoder[ClubPlayersResponse] = deriveEncoder
  }

  object ClubSquadResponse {

    def squadToSquadResponse(clubSquad: ClubSquad): ClubSquadResponse = new ClubSquadResponse(
      size = clubSquad.size,
      averageAge = clubSquad.averageAge,
      foreigners = clubSquad.foreigners,
      nationalTeamPlayers = clubSquad.nationalTeamPlayers
    )

    implicit val clubSquadResponseDecoder: Decoder[ClubSquadResponse] = deriveDecoder
    implicit val clubSquadResponseEncoder: Encoder[ClubSquadResponse] = deriveEncoder

  }

  object ClubLeagueResponse {

    def leagueToLeagueResponse(clubLeague: ClubLeague): ClubLeagueResponse = new ClubLeagueResponse(
      id = clubLeague.id,
      name = clubLeague.name,
      countryID = clubLeague.countryID,
      countryName = clubLeague.countryName,
      tier = clubLeague.tier
    )

    implicit val clubLeagueResponseDecoder: Decoder[ClubLeagueResponse] = deriveDecoder
    implicit val clubLeagueResponseEncoder: Encoder[ClubLeagueResponse] = deriveEncoder

  }

  private object ClubPlayerResponse {

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
