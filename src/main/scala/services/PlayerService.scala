package services

import cats.effect._
import cats.implicits.toFunctorOps
import errors.GameException.PlayerMarketValueNotFoundException
import errors.GameException.PlayerSearchByNameException
import errors.GameException.ValueParseException
import errors._
import httpClient.TransfermarktClient
import httpClient.domain.FetchedMarketValue
import httpClient.domain.PlayerSearch
import services.domain.MarketValue
import services.domain.PlayerSimple

import java.time.Instant
import scala.util.Try

trait PlayerService[F[_]] {
  def searchByName(playerName: String): F[Either[GameException, List[PlayerSimple]]]
  def getMarketValueByPlayerId(id: Int): F[Either[GameException, MarketValue]]
}

object PlayerService {

  def impl[F[_]: Sync](client: TransfermarktClient[F]) = new PlayerService[F] {

    val toPlayerSimple: PlayerSearch => PlayerSimple = { case PlayerSearch(id, name, position, club, age, nationality, marketValue) =>
      PlayerSimple(
        id = id.getOrElse(0),
        name = name.getOrElse("-"),
        position = position.getOrElse("-"),
        club = club.flatMap(_.name).getOrElse("-"),
        age = age.getOrElse("-"),
        nationality = nationality.getOrElse("-"),
        marketValue = parseMarketValue(marketValue) match {
          case Left(err)    => println(err); BigDecimal(0)
          case Right(value) => value
        }
      )
    }

    val toMarketValue: FetchedMarketValue => MarketValue = { case FetchedMarketValue(marketValue, updatedAt) =>
      MarketValue(
        marketValue = parseMarketValue(marketValue) match {
          case Left(err)    => println(err); BigDecimal(0)
          case Right(value) => value
        },
        updatedAt = updatedAt
          .map(_.take(20).concat("00Z"))
          .map(Instant.parse)
          .getOrElse(Instant.MIN)
      )
    }

    // 2023-12-19T11:30:36.754874
    // 2007-12-03T10:15:30.00Z.

    private def parseMarketValue(value: Option[String]): Either[ValueParseException, BigDecimal] = Try {
      val str = value.get
      val strWithNoEuro = str.drop(1)
      strWithNoEuro.toList match {
        case value :+ 'k' => BigDecimal(value.mkString.toDouble * 1_000)
        case value :+ 'm' => BigDecimal(value.mkString.toDouble * 1_000_000)
        case _            => BigDecimal(0)
      }
    }.toEither.left.map((err: Throwable) => ValueParseException(value, err))

    override def searchByName(playerName: String): F[Either[GameException, List[PlayerSimple]]] =
      client
        .searchByName(playerName)
        .map(_.map(_.map(toPlayerSimple)) match {
          case Right(playersList) => Right(playersList)
          case Left(err)          => Left(PlayerSearchByNameException(playerName, err.getMessage))
        })

    override def getMarketValueByPlayerId(id: Int): F[Either[GameException, MarketValue]] =
      client.getMarketValueByPlayerId(id).map(_.map(toMarketValue)).map {
        case Right(marketValue) => Right(marketValue)
        case Left(err)          => Left(PlayerMarketValueNotFoundException(id, err.getMessage))
      }

  }

}
