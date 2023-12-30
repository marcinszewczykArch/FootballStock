package game.player.service

import cats.effect._
import cats.implicits.catsSyntaxApplicativeError
import cats.implicits.toFunctorOps
import game.errors.GameException
import game.errors.GameException.PlayerMarketValueNotFoundException
import game.errors.GameException.PlayerProfileNotFoundException
import game.errors.GameException.PlayerSearchByNameException
import game.player.client.{PlayerProfileClient, PlayerSearchClient}
import game.player.client.domain.FetchedMarketValue
import game.player.client.domain.FetchedPlayerPosition
import game.player.client.domain.FetchedPlayerProfile
import game.player.client.domain.FetchedPlayerSimple
import game.player.service.domain._
import utils.Parser.parseInstant
import utils.Parser.parseMarketValueToBigDecimal

trait PlayerService[F[_]] {
  def searchByName(playerName: String): F[Either[GameException, List[PlayerSimple]]]
  def getMarketValueByPlayerId(id: PlayerId): F[Either[GameException, MarketValue]]
  def getPlayerProfileById(id: PlayerId): F[Either[GameException, PlayerProfile]]
}

object PlayerService {

  def impl[F[_]: Sync](
    playerProfileClient: PlayerProfileClient[F],
    playerSearchClient: PlayerSearchClient[F]
  ) = new PlayerService[F] {

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

    val toMarketValue: FetchedPlayerProfile => MarketValue = { playerProfile =>
      MarketValue(
        value = parseMarketValueToBigDecimal(playerProfile.marketValue) match {
          case Left(err)    => println(s"Not able to get player value. Get 0 instead. Reason: $err"); BigDecimal(0)
          case Right(value) => value
        }
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
      playerSearchClient
        .searchByName(playerName)
        .map(_.map(toPlayerSimple))
        .attempt
        .map {
          case Right(playersList) => Right(playersList)
          case Left(err)          => Left(PlayerSearchByNameException(playerName, err.getMessage))
        }

    override def getMarketValueByPlayerId(id: PlayerId): F[Either[GameException, MarketValue]] =
      playerProfileClient
        .fetchPlayerProfileById(id)
        .map(toMarketValue)
        .attempt
        .map {
          case Right(marketValue) => Right(marketValue)
          case Left(err)          => Left(PlayerProfileNotFoundException(id.value, err.getMessage))
        }

    override def getPlayerProfileById(id: PlayerId): F[Either[GameException, PlayerProfile]] =
      playerProfileClient
        .fetchPlayerProfileById(id)
        .map(toPlayerProfile)
        .attempt
        .map {
          case Right(playerProfile) => Right(playerProfile)
          case Left(err)            => Left(PlayerProfileNotFoundException(id.value, err.getMessage))
        }

  }

}
