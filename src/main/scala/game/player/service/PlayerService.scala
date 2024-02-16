package game.player.service

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.{catsSyntaxApplicativeError, toFlatMapOps, toFunctorOps}
import game.GameException.{PlayerMarketValueException, PlayerSearchByNameException}
import game.player.client.{PlayerMarketValueClient, PlayerProfileClient, PlayerSearchClient, PlayerStatsClient}
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerMapper._
import game.player.service.domain._
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.Type.ErrorOr

trait PlayerService[F[_]] {
  def searchByName(playerName: String): F[ErrorOr[List[PlayerSimple]]]
  def getMarketValueByPlayerId(id: PlayerId): F[ErrorOr[BigDecimal]]
  def getPlayerProfileById(id: PlayerId): F[ErrorOr[PlayerProfile]]
  def updateAndGetPlayerProfileById(id: PlayerId): F[ErrorOr[PlayerProfile]]
  def getMarketValueHistoryById(id: PlayerId): F[ErrorOr[MarketValueHistory]]
  def getPlayerStatsById(id: PlayerId): F[ErrorOr[PlayerStats]]
}

object PlayerService {

  def impl[F[_]: Sync: LoggerFactory](
    playerProfileClientMemory: PlayerProfileClientMemory[F],
    playerProfileClient: PlayerProfileClient[F],
    playerSearchClient: PlayerSearchClient[F],
    playerMarketValueClient: PlayerMarketValueClient[F],
    playerStatsClient: PlayerStatsClient[F]
  ) = new PlayerService[F] {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayerService[F]].getName)

    override def searchByName(playerName: String): F[ErrorOr[List[PlayerSimple]]] =
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

    override def getMarketValueByPlayerId(id: PlayerId): F[ErrorOr[BigDecimal]] =
      getPlayerProfileById(id).map(_.map(_.marketValue))

    override def getPlayerProfileById(id: PlayerId): F[ErrorOr[PlayerProfile]] = (for {
      json                 <- EitherT(playerProfileClientMemory.getById(id))
      fetchedPlayerProfile <- EitherT.fromEither(jsonToFetchedPlayerProfile(json))
      playerProfile = fetchedPlayerProfileToProfile(fetchedPlayerProfile)
    } yield playerProfile).value

    override def updateAndGetPlayerProfileById(
      id: PlayerId
    ): F[ErrorOr[PlayerProfile]] = (for {
      json                 <- EitherT(playerProfileClient.fetchRawPlayerProfileById(id))
      _                    <- EitherT(playerProfileClientMemory.save(id)(json))
      fetchedPlayerProfile <- EitherT.fromEither(jsonToFetchedPlayerProfile(json))
      playerProfile = fetchedPlayerProfileToProfile(fetchedPlayerProfile)
    } yield playerProfile).value

    override def getMarketValueHistoryById(id: PlayerId): F[ErrorOr[MarketValueHistory]] =
      playerMarketValueClient
        .fetchRawMarketValueHistoryById(id)
        .map(fetchedMarketValueHistoryToMarketValueHistory)
        .attempt
        .flatMap {
          case Right(marketValueHistory) =>
            Applicative[F].pure(Right(marketValueHistory))
          case Left(err)                 =>
            log
              .error(s"Getting Player Market Value history for '$id' failed: ${err.getMessage}")
              .as(
                Left(PlayerMarketValueException(id, err.getMessage))
              )
        }

    override def getPlayerStatsById(id: PlayerId): F[ErrorOr[PlayerStats]] = (for {
      fetchedPlayerStats <- EitherT.liftF(playerStatsClient.fetchRawPlayerStatsById(id))
      playerStats = fetchedPlayerStatsToStats(fetchedPlayerStats)
    } yield playerStats).value

  }

}
