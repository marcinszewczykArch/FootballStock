package game.modules.club.service

import game.GameException
import GameException.JsonDecodingException
import game.modules.club.client.domain.{FetchedClubLeague, FetchedClubPlayer, FetchedClubPlayers, FetchedClubProfile, FetchedClubSimple, FetchedClubSquad}
import game.modules.club.service.domain.{ClubId, ClubLeague, ClubPlayer, ClubPlayers, ClubProfile, ClubSimple, ClubSquad}
import game.modules.player.service.domain.PlayerId
import io.circe.Json
import utils.Parser.{toBigDecimalOrZero, toInstantOrFarPastForUpdateAt}
import utils.Type.ErrorOr

object ClubMapper {

  val fetchedClubSimpleToClubSimple: FetchedClubSimple => ClubSimple = {
    case FetchedClubSimple(id, url, name, country, squad, marketValue) =>
      ClubSimple(
        id = ClubId(id.getOrElse(0)),
        url = url.getOrElse("-"),
        name = name.getOrElse("-"),
        country = country.getOrElse("-"),
        squad = squad.getOrElse(0),
        marketValue = marketValue.getOrElse("-")
      )

  }

  val fetchedClubToClub: FetchedClubProfile => ClubProfile = {
    case FetchedClubProfile(
          id,
          url,
          name,
          officialName,
          image,
          website,
          foundedOn,
          stadiumName,
          stadiumSeats,
          currentMarketValue,
          squad,
          league,
          updatedAt
        ) =>
      ClubProfile(
        id = ClubId(id.getOrElse(0)),
        url = url.getOrElse("-"),
        name = name.getOrElse("-"),
        officialName = officialName.getOrElse("-"),
        image = image.getOrElse("-"),
        website = website.getOrElse("-"),
        foundedOn = foundedOn.getOrElse("-"),
        stadiumName = stadiumName.getOrElse("-"),
        stadiumSeats = stadiumSeats.getOrElse(0),
        currentMarketValue = toBigDecimalOrZero(currentMarketValue),
        squad = squad.map(fetchedClubSquadToClubSquad).getOrElse(ClubSquad.empty),
        league = league.map(fetchedClubLeagueToClubLeague).getOrElse(ClubLeague.empty),
        updatedAt = toInstantOrFarPastForUpdateAt(updatedAt)
      )

  }

  val fetchedClubSquadToClubSquad: FetchedClubSquad => ClubSquad = {
    case FetchedClubSquad(
          size,
          averageAge,
          foreigners,
          nationalTeamPlayers
        ) =>
      ClubSquad(
        size = size.getOrElse(0),
        averageAge = averageAge.getOrElse(0),
        foreigners = foreigners.getOrElse(0),
        nationalTeamPlayers = nationalTeamPlayers.getOrElse(0)
      )
  }

  val fetchedClubLeagueToClubLeague: FetchedClubLeague => ClubLeague = {
    case FetchedClubLeague(
          id,
          name,
          countryID,
          countryName,
          tier
        ) =>
      ClubLeague(
        id = id.getOrElse("-"),
        name = name.getOrElse("-"),
        countryID = countryID.getOrElse(0),
        countryName = countryName.getOrElse("-"),
        tier = tier.getOrElse("-")
      )
  }

  val fetchedClubPlayersToClubPlayers: FetchedClubPlayers=> ClubPlayers = {
    case FetchedClubPlayers(
    id,
    players,
    updatedAt
        ) =>
      ClubPlayers(
        id = ClubId(id.getOrElse(0)),
        players = players.map(_.map(fetchedClubPlayerToClubPlayer)).getOrElse(Nil),
        updatedAt = toInstantOrFarPastForUpdateAt(updatedAt)
      )
  }

  val fetchedClubPlayerToClubPlayer: FetchedClubPlayer => ClubPlayer = {
    case FetchedClubPlayer(
    id,
    name,
    position,
    dateOfBirth,
    age,
    nationality,
    height,
    foot,
    joinedOn,
    joined,
    signedFrom,
    contract,
    marketValue,
        ) =>
      ClubPlayer(
        id = PlayerId(id.getOrElse(0)),
        name = name.getOrElse("-"),
        position = position.getOrElse("-"),
        dateOfBirth = dateOfBirth.getOrElse("-"),
        age = age.getOrElse(0),
        nationality = nationality.getOrElse(Nil),
        height = height.getOrElse("-"),
        foot = foot.getOrElse("-"),
        joinedOn = joinedOn.getOrElse("-"),
        joined = joined.getOrElse("-"),
        signedFrom = signedFrom.getOrElse("-"),
        contract = contract.getOrElse("-"),
        marketValue = toBigDecimalOrZero(marketValue)
      )
  }

  val jsonToFetchedClubProfile: Json => ErrorOr[FetchedClubProfile] =
    _.as[FetchedClubProfile].left.map(decodingFailure => JsonDecodingException(decodingFailure.getMessage()))

  val jsonToFetchedClubPlayers: Json => ErrorOr[FetchedClubPlayers] =
    _.as[FetchedClubPlayers].left.map(decodingFailure => JsonDecodingException(decodingFailure.getMessage()))

}
