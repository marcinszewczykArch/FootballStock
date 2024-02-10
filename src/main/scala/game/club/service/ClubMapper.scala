package game.club.service

import game.club.client.domain.{FetchedClubLeague, FetchedClubProfile, FetchedClubSimple, FetchedClubSquad}
import game.club.service.domain._
import game.errors.GameException
import game.errors.GameException.JsonDecodingException
import io.circe.Json
import utils.Parser.{toBigDecimalOrZero, toInstantOrFarPastForUpdateAt}

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
        stadiumSeats = stadiumSeats.getOrElse("-"),
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

  val jsonToFetchedClubProfile: Json => Either[GameException, FetchedClubProfile] =
    _.as[FetchedClubProfile].left.map(decodingFailure => JsonDecodingException(decodingFailure.getMessage()))

}
