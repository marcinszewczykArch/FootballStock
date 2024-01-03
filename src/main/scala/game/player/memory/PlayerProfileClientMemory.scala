package game.player.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import config.AppConfig.TransfermarktClientConfig
import game.errors.GameException
import game.errors.GameException.{PlayerJsonNotFoundInMemoryCacheException, PlayerJsonNotFoundInMemoryException}
import game.player.client.PlayerProfileClient
import game.player.service.domain.PlayerId
import io.circe.Json
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import utils.Cache
import cats.syntax.all._

trait PlayerProfileClientMemory[F[_]] {

  def savePlayerJson(playerId: PlayerId)(playerJson: Json): F[Either[GameException, Unit]]
  def getPlayerJson(playerId: PlayerId): F[Either[GameException, Json]]

}

object PlayerProfileClientMemory {

  def cachedInstance[F[_]: Sync: LoggerFactory](
    config: TransfermarktClientConfig,
    playerProfileClient: PlayerProfileClient[F],
    underlying: PlayerProfileClientMemory[F]
  ): PlayerProfileClientMemory[F] = {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayerProfileClientMemory[F]].getName)

    val fetchRawPlayersProfileCache: Cache[F, PlayerId, Json] =
      Cache.instance[F, PlayerId, Json](
        cacheName = config.cacheName
      )(
        lookup = playerId =>
          log.info(s"player $playerId not found in cache. Checking memory...") *>
            underlying.getPlayerJson(playerId).flatMap {
            case Right(json) => Applicative[F].pure(json)
            case Left(err)  => (for {
              _    <- EitherT.liftF(log.info(s"${err.getMessage} Calling http client..."))
              json <- EitherT(playerProfileClient.fetchRawPlayerProfileById(playerId))
              _    <- EitherT(underlying.savePlayerJson(playerId)(json))
            } yield json).rethrowT
          }
      )(
        ttl = config.cacheTtl,
        failedFetchTtl = config.failedCacheTtl
      )

    new PlayerProfileClientMemory[F] {
      override def savePlayerJson(playerId: PlayerId)(playerJson: Json): F[Either[GameException, Unit]] =
        underlying.savePlayerJson(playerId)(playerJson)
      override def getPlayerJson(playerId: PlayerId): F[Either[GameException, Json]] =
        fetchRawPlayersProfileCache.get(playerId).attempt.map(_.left.map(_ => PlayerJsonNotFoundInMemoryCacheException(playerId)))
    }
  }

  def impl[F[_]](
    ref: Ref[F, Map[PlayerId, Json]]
  )(
    implicit F: Sync[F]
  ): PlayerProfileClientMemory[F] =
    new PlayerProfileClientMemory[F] {

      override def savePlayerJson(playerId: PlayerId)(playerJson: Json): F[Either[GameException, Unit]] = (for {
        json <- EitherT.right[GameException](ref.update(_ + (playerId -> playerJson)))
      } yield json).value

      override def getPlayerJson(playerId: PlayerId): F[Either[GameException, Json]] = ref
        .get
        .map(_.get(playerId) match {
          case Some(playerJson) => Right(playerJson)
          case None             => Left(PlayerJsonNotFoundInMemoryException(playerId))
        })

    }

}
