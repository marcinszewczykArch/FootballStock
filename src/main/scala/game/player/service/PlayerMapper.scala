package game.player.service

import game.GameException
import game.GameException.JsonDecodingException
import game.club.service.domain.ClubId
import game.player.client.domain._
import game.player.service.domain._
import io.circe.Json
import utils.Parser.{toBigDecimalOrZero, toInstantOrFarPastForDate, toInstantOrFarPastForUpdateAt}
import utils.Type.ErrorOr

import scala.util.Try

object PlayerMapper {

  val defaultImageUrl = "https://img.a.transfermarkt.technology/portrait/header/default.jpg?lm=1"

  val fetchedPlayerSimpleToPlayerSimple: FetchedPlayerSimple => PlayerSimple = {
    case FetchedPlayerSimple(id, name, position, club, age, nationalities, marketValue) =>
      PlayerSimple(
        id = PlayerId(id.getOrElse(0)),
        name = name.getOrElse("-"),
        position = position.getOrElse("-"),
        club = club.flatMap(_.name).getOrElse("-"),
        clubId = ClubId(club.flatMap(_.id).getOrElse(0)),
        age = age.getOrElse("-"),
        nationalities = nationalities.getOrElse(Nil),
        marketValue = toBigDecimalOrZero(marketValue)
      )
  }

  val fetchedPlayerProfileToMarketValue: FetchedPlayerProfile => BigDecimal = { playerProfile =>
    toBigDecimalOrZero(playerProfile.marketValue)
  }

  //  /robert-lewandowski/profil/spieler/38253
  private def nameFromUrl(maybeUrl: Option[String]): Option[String] = maybeUrl.flatMap { url =>
    Try(url.split("/")(1))
      .toOption
      .map(_.split("-")
        .map(_.capitalize)
        .mkString(" ")
      )
  }

  val fetchedPlayerProfileToProfile: FetchedPlayerProfile => PlayerProfile = {
    case FetchedPlayerProfile(
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
        ) =>
      PlayerProfile(
        id = PlayerId(id.flatMap(_.toIntOption).getOrElse(0)),
        url = url.getOrElse("-"),
        name = nameFromUrl(url).getOrElse(name.getOrElse("-")),
        description = description.getOrElse("-"),
        imageURL = imageURL.getOrElse(defaultImageUrl),
        dateOfBirth = dateOfBirth.getOrElse("-"),
        citizenship = citizenship.getOrElse(Nil).toList,
        isRetired = isRetired.getOrElse(true),
        position = position
          .map { case FetchedPlayerPosition(main, others) =>
            PlayerPosition(main.getOrElse("-"), others.getOrElse(Nil))
          }
          .getOrElse(PlayerPosition.empty),
        clubId = club.flatMap(_.id).getOrElse(0),
        club = club.flatMap(_.name).getOrElse("-"),
        marketValue = toBigDecimalOrZero(marketValue),
        updatedAt = toInstantOrFarPastForUpdateAt(updatedAt)
      )

  }

  val jsonToFetchedPlayerProfile: Json => ErrorOr[FetchedPlayerProfile] =
    _.as[FetchedPlayerProfile].left.map(decodingFailure => JsonDecodingException(decodingFailure.getMessage()))

  val fetchedMarketValueHistoryToMarketValueHistory: FetchedMarketValueHistory => MarketValueHistory = {
    case FetchedMarketValueHistory(id, marketValue, marketValueHistory, updatedAt) =>
      MarketValueHistory(
        id = PlayerId(id.getOrElse(0)),
        marketValue = toBigDecimalOrZero(marketValue),
        marketValueHistory = marketValueHistory.map(fetchedMarketValueToMarketValue),
        updatedAt = toInstantOrFarPastForUpdateAt(updatedAt)
      )
  }

  val fetchedMarketValueToMarketValue: FetchedMarketValue => MarketValue = { case FetchedMarketValue(age, date, clubName, value, clubId) =>
    MarketValue(
      age = age.getOrElse(0),
      date = toInstantOrFarPastForDate(date),
      clubName = clubName.getOrElse("-"),
      value = toBigDecimalOrZero(value),
      clubId = clubId.getOrElse(0)
    )
  }

  val fetchedPlayerStatsToStats: FetchedPlayerStats => PlayerStats = { case FetchedPlayerStats(id, stats, updatedAt) => {
    val domainStats = stats.map(_.map(fetchedStatsToStats)).getOrElse(Nil)
    PlayerStats(
      id = PlayerId(id.getOrElse(0)),
      stats = domainStats,
      totalMinutesPlayed = domainStats.map(_.minutesPlayed).sum,
      updatedAt = toInstantOrFarPastForUpdateAt(updatedAt)
    )
  }
    }

  val fetchedStatsToStats: FetchedStat => Stat = {
    case FetchedStat(
          competitionID,
          clubID,
          seasonID,
          competitionName,
          appearances,
          goals,
          yellowCards,
          minutesPlayed
        ) =>
      Stat(
        competitionID = competitionID.getOrElse("-"),
        clubID = ClubId(clubID.getOrElse(0)),
        seasonID = seasonID.getOrElse("-"),
        competitionName = competitionName.getOrElse("-"),
        appearances = appearances.getOrElse(0),
        goals = goals.getOrElse(0),
        yellowCards = yellowCards.getOrElse(0),
        minutesPlayed = minutesPlayed.flatMap(_.dropRight(1).replace(".","").toIntOption).getOrElse(0)
      )
  }

}
