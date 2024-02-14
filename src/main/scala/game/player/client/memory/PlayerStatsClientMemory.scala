package game.player.client.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import cats.syntax.all._
import config.AppConfig.{PlayerProfileClientConfig, PlayerStatsClientConfig}
import game.GameException
import GameException.{DynamoReaderException, JsonParsingFailure, PlayerProfileJsonNotFoundInMemoryCacheException, PlayerStatsJsonNotFoundInMemoryCacheException}
import game.player.client.{PlayerProfileClient, PlayerStatsClient}
import game.player.service.domain.PlayerId
import io.circe.Json
import io.circe.parser
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import utils.Cache
import utils.Type.ErrorOr

trait PlayerStatsClientMemory[F[_]] {

  def getById(playerId: PlayerId): F[ErrorOr[Json]]
  def getAll(): F[Map[PlayerId, Json]]
  def save(playerId: PlayerId)(playerJson: Json): F[ErrorOr[Unit]]

}

object PlayerStatsClientMemory {

  def cachedInstance[F[_]: Sync: LoggerFactory](
    config: PlayerStatsClientConfig,
    playerStatsClient: PlayerStatsClient[F],
    underlying: PlayerStatsClientMemory[F]
  ): PlayerStatsClientMemory[F] = {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayerStatsClientMemory[F]].getName)

    val fetchRawPlayersStatsCache: Cache[F, PlayerId, Json] =
      Cache.instance[F, PlayerId, Json](
        cacheName = config.cacheName
      )(
        lookup = playerId =>
          log.debug(s"player stats for $playerId not found in cache. Checking memory...") *>
            underlying.getById(playerId).flatMap {
              case Right(json) => Applicative[F].pure(json)
              case Left(err)   =>
                (for {
                  _    <- EitherT.liftF(log.debug(s"${err.getMessage} Calling http client..."))
                  json <- EitherT(playerStatsClient.fetchRawPlayerStatsById(playerId))
                  _    <- EitherT(underlying.save(playerId)(json))
                } yield json).rethrowT
            }
      )(
        ttl = config.cacheTtl,
        failedFetchTtl = config.failedCacheTtl
      )

    new PlayerStatsClientMemory[F] {
      def getById(playerId: PlayerId): F[ErrorOr[Json]]                =
        fetchRawPlayersStatsCache
          .get(playerId)
          .attempt
          .map(_.left.map(_ => PlayerStatsJsonNotFoundInMemoryCacheException(playerId)))
      def getAll(): F[Map[PlayerId, Json]]                                           = underlying.getAll()
      def save(playerId: PlayerId)(playerJson: Json): F[ErrorOr[Unit]] = (for {
        _ <- EitherT(underlying.save(playerId)(playerJson))
        _ <- EitherT.liftF[F, GameException, Json](fetchRawPlayersStatsCache.update(playerId)(playerJson))
      } yield ()).value

    }
  }

  def impl[F[_]: Sync: LoggerFactory](scanamo: Scanamo): PlayerStatsClientMemory[F] =
    new PlayerStatsClientMemory[F] {
      import org.scanamo._
      import org.scanamo.generic.auto._
      import org.scanamo.syntax._

      implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayerProfileClientMemory[F]].getName)

      private val SOURCE_TRANSFERMARKT = "Transfermarkt"
      private val table                = Table[PlayerStatsTable]("PlayerStats")
      private case class PlayerStatsTable(source: String, playerId: Int, json: String)

      override def save(playerId: PlayerId)(playerJson: Json): F[ErrorOr[Unit]] =
        log.debug(s"saving player stats for $playerId to dynamoDb") *> scanamo
          .exec(
            table.put(
              PlayerStatsTable(
                source = SOURCE_TRANSFERMARKT,
                playerId = playerId.value,
                json = playerJson.toString()
              )
            )
          )
          .asRight[GameException]
          .pure

      override def getById(
        playerId: PlayerId
      ): F[ErrorOr[Json]] =
        log.debug(s"getting player stats json $playerId from dynamoDb") *> (scanamo
          .exec(table.get("source" === SOURCE_TRANSFERMARKT and "playerId" === playerId.value.toLong))
          .map(_.left.map(err => DynamoReaderException(err.toString))) match {
          case Some(value) =>
            value
              .map(_.json)
              .flatMap(str =>
                parser.parse(str) match {
                  case Left(parsingFailure) => Left[GameException, Json](JsonParsingFailure(parsingFailure.getMessage()))
                  case Right(json)          => Right[GameException, Json](json)
                }
              )
              .pure

          case None => Applicative[F].pure(Left[GameException, Json](DynamoReaderException(s"Result for $playerId not found in memory.")))
        })

      override def getAll(): F[Map[PlayerId, Json]] =
        log.debug(s"getting all player stats json from dynamoDb") *> scanamo
          .exec(table.scan())
          .sequence
          .getOrElse(Nil)
          .mapFilter { record =>
            val playerId = PlayerId(record.playerId)
            parser.parse(record.json).toOption.map(json => playerId -> json)
          }
          .toMap
          .pure

    }

}
