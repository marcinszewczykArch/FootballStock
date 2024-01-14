package game.player.service

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.catsSyntaxApplicativeError
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import game.errors.GameException
import game.errors.GameException.JsonDecodingException
import game.errors.GameException.PlayerMarketValueNotFoundException
import game.errors.GameException.PlayerProfileNotFoundException
import game.errors.GameException.PlayerSearchByNameException
import game.player.client.PlayerProfileClient
import game.player.client.PlayerSearchClient
import game.player.client.domain.FetchedMarketValue
import game.player.client.domain.FetchedPlayerPosition
import game.player.client.domain.FetchedPlayerProfile
import game.player.client.domain.FetchedPlayerSimple
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.domain._
import io.circe.DecodingFailure
import io.circe.Json
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import utils.Parser.toBigDecimalOrZero
import utils.Parser.toInstantOrFarPast

object PlayerMapper {

  val fetchedPlayerSimpleToPlayerSimple: FetchedPlayerSimple => PlayerSimple = {
    case FetchedPlayerSimple(id, name, position, club, age, nationality, marketValue) =>
      PlayerSimple(
        id = PlayerId(id.getOrElse(0)),
        name = name.getOrElse("-"),
        position = position.getOrElse("-"),
        club = club.flatMap(_.name).getOrElse("-"),
        age = age.getOrElse("-"),
        nationality = nationality.getOrElse("-"),
        marketValue = toBigDecimalOrZero(marketValue)
      )
  }

  val fetchedPlayerProfileToMarketValue: FetchedPlayerProfile => MarketValue = { playerProfile =>
    MarketValue(toBigDecimalOrZero(playerProfile.marketValue))
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
        club = club.flatMap(_.name).getOrElse("-"),
        marketValue = toBigDecimalOrZero(marketValue),
        updatedAt = toInstantOrFarPast(updatedAt)
      )

  }

  val jsonToFetchedPlayerProfile: Json => Either[GameException, FetchedPlayerProfile] =
    _.as[FetchedPlayerProfile].left.map(decodingFailure => JsonDecodingException(decodingFailure.getMessage()))

}
