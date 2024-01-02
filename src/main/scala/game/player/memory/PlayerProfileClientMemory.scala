package game.player.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import config.AppConfig.TransfermarktClientConfig
import game.errors.GameException
import game.errors.GameException.PlayerJsonNotFoundInMemoryException
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
    ref: Ref[F, Map[PlayerId, Json]]
  ): PlayerProfileClientMemory[F] = {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayerProfileClientMemory[F]].getName)

    val playerProfileClientMemory = PlayerProfileClientMemory.impl[F](ref)
    val fetchRawPlayersProfileCache: Cache[F, PlayerId, Json] =
      Cache.instance[F, PlayerId, Json](
        cacheName = config.cacheName
      )(
        lookup = playerId =>
          log.info(s"player $playerId not found in cache. Checking memory.") *>
            playerProfileClientMemory.getPlayerJson(playerId).flatMap(_.liftTo)
      )(
        ttl = config.cacheTtl,
        failedFetchTtl = config.failedCacheTtl
      )

    new PlayerProfileClientMemory[F] {
      override def savePlayerJson(playerId: PlayerId)(playerJson: Json): F[Either[GameException, Unit]] =
        playerProfileClientMemory.savePlayerJson(playerId)(playerJson)
      override def getPlayerJson(playerId: PlayerId): F[Either[GameException, Json]] =
        fetchRawPlayersProfileCache.get(playerId).map(Right(_))
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
          case None             => Left(PlayerJsonNotFoundInMemoryException(playerId.value))
        })

    }

}
