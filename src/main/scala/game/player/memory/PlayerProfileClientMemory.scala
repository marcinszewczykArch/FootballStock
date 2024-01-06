package game.player.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.{catsSyntaxApplyOps, toFlatMapOps, toFunctorOps}
import cats.syntax.all._
import config.AppConfig.TransfermarktClientConfig
import game.errors.GameException
import game.errors.GameException.{DynamoReaderException, JsonParsingFailure, PlayerJsonNotFoundInMemoryCacheException, PlayerJsonNotFoundInMemoryException}
import game.player.client.PlayerProfileClient
import game.player.service.domain.PlayerId
import io.circe.{Json, parser}
import org.scanamo.Scanamo
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.Cache

trait PlayerProfileClientMemory[F[_]] {

  def getById(playerId: PlayerId): F[Either[GameException, Json]]
  def getAll(): F[Map[PlayerId, Json]]
  def save(playerId: PlayerId)(playerJson: Json): F[Either[GameException, Unit]]

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
            underlying.getById(playerId).flatMap {
              case Right(json) => Applicative[F].pure(json)
              case Left(err)   =>
                (for {
                  _    <- EitherT.liftF(log.info(s"${err.getMessage} Calling http client..."))
                  json <- EitherT(playerProfileClient.fetchRawPlayerProfileById(playerId))
                  _    <- EitherT(underlying.save(playerId)(json))
                } yield json).rethrowT
            }
      )(
        ttl = config.cacheTtl,
        failedFetchTtl = config.failedCacheTtl
      )

    new PlayerProfileClientMemory[F] {
      def getById(playerId: PlayerId): F[Either[GameException, Json]] =
        fetchRawPlayersProfileCache
          .get(playerId)
          .attempt
          .map(_.left.map(_ => PlayerJsonNotFoundInMemoryCacheException(playerId)))
      def getAll(): F[Map[PlayerId, Json]] = underlying.getAll()
      def save(playerId: PlayerId)(playerJson: Json): F[Either[GameException, Unit]] =
        underlying.save(playerId)(playerJson)
    }
  }

  def implRef[F[_]](
    ref: Ref[F, Map[PlayerId, Json]]
  )(
    implicit F: Sync[F]
  ): PlayerProfileClientMemory[F] =
    new PlayerProfileClientMemory[F] {

      override def save(playerId: PlayerId)(playerJson: Json): F[Either[GameException, Unit]] = (for {
        json <- EitherT.right[GameException](ref.update(_ + (playerId -> playerJson)))
      } yield json).value

      override def getById(playerId: PlayerId): F[Either[GameException, Json]] = ref
        .get
        .map(_.get(playerId) match {
          case Some(playerJson) => Right(playerJson)
          case None             => Left(PlayerJsonNotFoundInMemoryException(playerId))
        })

      override def getAll(): F[Map[PlayerId, Json]] = ref.get

    }

  def implDynamoDb[F[_]: Sync: LoggerFactory](scanamo: Scanamo): PlayerProfileClientMemory[F] =
    new PlayerProfileClientMemory[F] {
      import org.scanamo._
      import org.scanamo.generic.auto._
      import org.scanamo.syntax._

      implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayerProfileClientMemory[F]].getName)

      private val SOURCE_TRANSFERMARKT = "Transfermarkt"
      private val table = Table[PlayerProfileTable]("PlayerProfile")
      private case class PlayerProfileTable(source: String, playerId: Int, json: String)

      override def save(
        playerId: PlayerId
      )(
        playerJson: Json
      ): F[Either[GameException, Unit]] = log.info(s"saving player $playerId to dynamoDb") *> scanamo
        .exec(
          table.put(
            PlayerProfileTable(
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
      ): F[Either[GameException, Json]] = scanamo
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
      }

      override def getAll(): F[Map[PlayerId, Json]] = scanamo
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
