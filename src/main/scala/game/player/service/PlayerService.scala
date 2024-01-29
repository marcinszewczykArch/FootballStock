package game.player.service

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.catsSyntaxApplicativeError
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import game.errors.GameException
import game.errors.GameException.PlayerSearchByNameException
import game.player.client.PlayerSearchClient
import game.player.client.domain.FetchedPlayerProfile
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerMapper.fetchedPlayerProfileToMarketValue
import game.player.service.PlayerMapper.fetchedPlayerProfileToProfile
import game.player.service.PlayerMapper.fetchedPlayerSimpleToPlayerSimple
import game.player.service.PlayerMapper.jsonToFetchedPlayerProfile
import game.player.service.domain._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger

trait PlayerService[F[_]] {
  def searchByName(playerName: String): F[Either[GameException, List[PlayerSimple]]]
  def getMarketValueByPlayerId(id: PlayerId): F[Either[GameException, BigDecimal]]
  def getPlayerProfileById(id: PlayerId): F[Either[GameException, PlayerProfile]]
}

object PlayerService {

  def impl[F[_]: Sync: LoggerFactory](
    playerProfileClientMemory: PlayerProfileClientMemory[F],
    playerSearchClient: PlayerSearchClient[F]
  ) = new PlayerService[F] {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayerService[F]].getName)

    override def searchByName(playerName: String): F[Either[GameException, List[PlayerSimple]]] =
      playerSearchClient
        .searchByName(playerName)
        .map(_.map(fetchedPlayerSimpleToPlayerSimple))
        .attempt
        .flatMap {
          case Right(playersList) =>
            Applicative[F].pure(Right(playersList))
          case Left(err)          =>
            log
              .error(s"Player search for '$playerName' failed: ${err.getMessage}")
              .as(
                Left(PlayerSearchByNameException(playerName, err.getMessage))
              )
        }

    override def getMarketValueByPlayerId(id: PlayerId): F[Either[GameException, BigDecimal]] =
      getPlayerProfileById(id).map(_.map(_.marketValue))

    override def getPlayerProfileById(id: PlayerId): F[Either[GameException, PlayerProfile]] =
      playerIdToJson(id).map(_.map(fetchedPlayerProfileToProfile))

    private def playerIdToJson(id: PlayerId): F[Either[GameException, FetchedPlayerProfile]] = (for {
      json                 <- EitherT(playerProfileClientMemory.getById(id))
      fetchedPlayerProfile <- EitherT.fromEither(jsonToFetchedPlayerProfile(json))
    } yield fetchedPlayerProfile).value

  }

}
