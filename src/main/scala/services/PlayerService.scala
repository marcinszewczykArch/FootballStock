package services

import cats.effect._
import cats.implicits.toFunctorOps
import errors.GameException.{PlayerMarketValueNotFoundException, PlayerProfileNotFoundException, PlayerSearchByNameException, ValueParseException}
import errors._
import httpClient.TransfermarktClient
import httpClient.domain.FetchedMarketValue
import httpClient.domain.FetchedPlayerPosition
import httpClient.domain.FetchedPlayerProfile
import httpClient.domain.FetchedPlayerSimple
import services.domain.{MarketValue, PlayerId, PlayerPosition, PlayerProfile, PlayerSimple}
import utils.Parser.parseInstant
import utils.Parser.parseMarketValueToBigDecimal

trait PlayerService[F[_]] {
  def searchByName(playerName: String): F[Either[GameException, List[PlayerSimple]]]
  def getMarketValueByPlayerId(id: PlayerId): F[Either[GameException, MarketValue]]
  def getPlayerProfileById(id: PlayerId): F[Either[GameException, PlayerProfile]]
}

object PlayerService {

  def impl[F[_]: Sync](client: TransfermarktClient[F]) = new PlayerService[F] {

    val toPlayerSimple: FetchedPlayerSimple => PlayerSimple = {
      case FetchedPlayerSimple(id, name, position, club, age, nationality, marketValue) =>
        PlayerSimple(
          id = PlayerId(id.getOrElse(0)),
          name = name.getOrElse("-"),
          position = position.getOrElse("-"),
          club = club.flatMap(_.name).getOrElse("-"),
          age = age.getOrElse("-"),
          nationality = nationality.getOrElse("-"),
          marketValue = parseMarketValueToBigDecimal(marketValue) match {
            case Left(err)    => println(s"Not able to get player value. Get 0 instead. Reason: $err"); BigDecimal(0)
            case Right(value) => value
          }
        )
    }

    val toMarketValue: FetchedMarketValue => MarketValue = { case FetchedMarketValue(marketValue, updatedAt) =>
      MarketValue(
        value = parseMarketValueToBigDecimal(marketValue) match {
          case Left(err)    => println(s"Not able to get player value. Get 0 instead. Reason: $err"); BigDecimal(0)
          case Right(value) => value
        },
        updatedAt = parseInstant(updatedAt)
      )
    }

    val toPlayerProfile: FetchedPlayerProfile => PlayerProfile = {
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
          marketValue = parseMarketValueToBigDecimal(marketValue) match {
            case Left(err)    => println(s"Not able to get player value. Get 0 instead. Reason: $err"); BigDecimal(0)
            case Right(value) => value
          },
          updatedAt = parseInstant(updatedAt)
        )

    }

    override def searchByName(playerName: String): F[Either[GameException, List[PlayerSimple]]] =
      client
        .searchByName(playerName)
        .map(_.map(_.map(toPlayerSimple)) match {
          case Right(playersList) => Right(playersList)
          case Left(err)          => Left(PlayerSearchByNameException(playerName, err.getMessage))
        })

    override def getMarketValueByPlayerId(id: PlayerId): F[Either[GameException, MarketValue]] =
      client.fetchMarketValueByPlayerId(id).map(_.map(toMarketValue)).map {
        case Right(marketValue) => Right(marketValue)
        case Left(err)          => Left(PlayerMarketValueNotFoundException(id.value, err.getMessage))
      }

    override def getPlayerProfileById(id: PlayerId): F[Either[GameException, PlayerProfile]] =
      client.fetchPlayerProfileById(id).map(_.map(toPlayerProfile)).map {
        case Right(playerProfile) => Right(playerProfile)
        case Left(err)            => Left(PlayerProfileNotFoundException(id.value, err.getMessage))

      }

  }

}
