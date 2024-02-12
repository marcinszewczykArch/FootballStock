package game.player.service

import game.GameException
import GameException.JsonDecodingException
import game.player.client.domain._
import game.player.service.domain._
import io.circe.Json
import utils.Parser.{toBigDecimalOrZero, toInstantOrFarPastForDate, toInstantOrFarPastForUpdateAt}

object PlayerMapper {

  val fetchedPlayerSimpleToPlayerSimple: FetchedPlayerSimple => PlayerSimple = {
    case FetchedPlayerSimple(id, name, position, club, age, nationalities, marketValue) =>
      PlayerSimple(
        id = PlayerId(id.getOrElse(0)),
        name = name.getOrElse("-"),
        position = position.getOrElse("-"),
        club = club.flatMap(_.name).getOrElse("-"),
        age = age.getOrElse("-"),
        nationalities = nationalities.getOrElse(Nil),
        marketValue = toBigDecimalOrZero(marketValue)
      )
  }

  val fetchedPlayerProfileToMarketValue: FetchedPlayerProfile => BigDecimal = { playerProfile =>
    toBigDecimalOrZero(playerProfile.marketValue)
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
        name = name.getOrElse("-"),
        description = description.getOrElse("-"),
        imageURL = imageURL.getOrElse("-"),
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

  val jsonToFetchedPlayerProfile: Json => Either[GameException, FetchedPlayerProfile] =
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

  val fetchedMarketValueToMarketValue: FetchedMarketValue => MarketValue = {
    case FetchedMarketValue(age, date, clubName, value, clubId) =>
      MarketValue(
        age = age.getOrElse(0),
        date = toInstantOrFarPastForDate(date),
        clubName = clubName.getOrElse("-"),
        value = toBigDecimalOrZero(value),
        clubId = clubId.getOrElse(0),
      )
  }

}
