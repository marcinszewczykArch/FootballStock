package game.player.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.{catsSyntaxApplyOps, toFlatMapOps, toFunctorOps}
import cats.syntax.all._
import config.AppConfig.TransfermarktClientConfig
import game.errors.GameException
import game.errors.GameException.{DynamoReaderException, PlayerJsonNotFoundInMemoryCacheException, PlayerJsonNotFoundInMemoryException}
import game.player.client.PlayerProfileClient
import game.player.service.domain.PlayerId
import io.circe.{Json, parser}
import org.scanamo.Scanamo
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.Cache

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
              case Left(err)   =>
                (for {
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

  def implRef[F[_]](
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

  def implDynamoDb[F[_]: Sync: LoggerFactory](scanamo: Scanamo): PlayerProfileClientMemory[F] =
    new PlayerProfileClientMemory[F] {
      import org.scanamo._
      import org.scanamo.generic.auto._
      import org.scanamo.syntax._
      implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayerProfileClientMemory[F]].getName)

      case class PlayerProfileTable(playerId: String, playerActive: String, json: String)
      val table = Table[PlayerProfileTable]("PlayerProfile")

      def isActivePlayer(playerJson: Json): Boolean = playerJson.findAllByKey("isRetired").map(_.toString()).contains("false")

      override def savePlayerJson(
        playerId: PlayerId
      )(
        playerJson: Json
      ): F[Either[GameException, Unit]] = log.info(s"saving player $playerId to dynamoDb") *> scanamo
        .exec(
          table.put(
            PlayerProfileTable(
              playerId = playerId.value.toString,
              playerActive = isActivePlayer(playerJson).toString,
              json = playerJson.toString()
            )
          )
        )
        .asRight[GameException]
        .pure

      override def getPlayerJson(
        playerId: PlayerId
      ): F[Either[GameException, Json]] = scanamo
        .exec(table.get("playerId" === playerId.value.toString and "playerActive" === "true"))
        .map(
          _.left
            .map(err => DynamoReaderException(err.toString))
        ) match {
        case Some(value) =>
          Applicative[F].pure(value.map(playerProfile => parser.parse(playerProfile.json).toOption.get).leftWiden[GameException])
        case None        => Applicative[F].pure(Left[GameException, Json](DynamoReaderException("???")))
      }

    }

}
