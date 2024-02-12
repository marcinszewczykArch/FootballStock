package game.club.client.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.{catsSyntaxApplyOps, toFlatMapOps, toFunctorOps}
import cats.syntax.all._
import config.AppConfig.ClubPlayersClientConfig
import game.GameException
import game.club.client.ClubPlayersClient
import game.club.service.domain.ClubId
import GameException.{ClubPlayersJsonNotFoundInMemoryCacheException, DynamoReaderException, JsonParsingFailure}
import io.circe.{Json, parser}
import org.scanamo.Scanamo
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.Cache

trait ClubPlayersClientMemory[F[_]] {

  def getById(clubId: ClubId): F[Either[GameException, Json]]
  def save(clubId: ClubId)(clubJson: Json): F[Either[GameException, Unit]]

}

object ClubPlayersClientMemory {

  def cachedInstance[F[_]: Sync: LoggerFactory](
    config: ClubPlayersClientConfig,
    clubPlayersClient: ClubPlayersClient[F],
    underlying: ClubPlayersClientMemory[F]
  ): ClubPlayersClientMemory[F] = {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[ClubPlayersClientMemory[F]].getName)

    val fetchRawClubPlayersCache: Cache[F, ClubId, Json] =
      Cache.instance[F, ClubId, Json](
        cacheName = config.cacheName
      )(
        lookup = clubId =>
          log.debug(s"club players for $clubId not found in cache. Checking memory...") *>
            underlying.getById(clubId).flatMap {
              case Right(json) => Applicative[F].pure(json)
              case Left(err)   =>
                (for {
                  _    <- EitherT.liftF(log.debug(s"${err.getMessage} Calling http client..."))
                  json <- EitherT(clubPlayersClient.fetchRawClubPlayersById(clubId))
                  _    <- EitherT(underlying.save(clubId)(json))
                } yield json).rethrowT
            }
      )(
        ttl = config.cacheTtl,
        failedFetchTtl = config.failedCacheTtl
      )

    new ClubPlayersClientMemory[F] {
      def getById(clubId: ClubId): F[Either[GameException, Json]]              =
        fetchRawClubPlayersCache
          .get(clubId)
          .attempt
          .map(_.left.map(_ => ClubPlayersJsonNotFoundInMemoryCacheException(clubId)))
      def save(clubId: ClubId)(clubJson: Json): F[Either[GameException, Unit]] = (for {
        _ <- EitherT(underlying.save(clubId)(clubJson))
        _ <- EitherT.liftF[F, GameException, Json](fetchRawClubPlayersCache.update(clubId)(clubJson))
      } yield ()).value

    }
  }

  def impl[F[_]: Sync: LoggerFactory](scanamo: Scanamo): ClubPlayersClientMemory[F] =
    new ClubPlayersClientMemory[F] {
      import org.scanamo._
      import org.scanamo.generic.auto._
      import org.scanamo.syntax._

      implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[ClubPlayersClientMemory[F]].getName)

      private val SOURCE_TRANSFERMARKT = "Transfermarkt"
      private val table                = Table[ClubPlayersTable]("ClubPlayers")
      private case class ClubPlayersTable(source: String, clubId: Int, json: String)

      override def getById(clubId: ClubId): F[Either[GameException, Json]] =
        log.debug(s"getting club players json $clubId from dynamoDb") *> (scanamo
          .exec(table.get("source" === SOURCE_TRANSFERMARKT and "clubId" === clubId.value.toLong))
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

          case None => Applicative[F].pure(Left[GameException, Json](DynamoReaderException(s"Result for $clubId not found in memory.")))
        })

      override def save(clubId: ClubId)(clubJson: Json): F[Either[GameException, Unit]] =
        log.debug(s"saving club players for $clubId to dynamoDb") *> scanamo
          .exec(
            table.put(
              ClubPlayersTable(
                source = SOURCE_TRANSFERMARKT,
                clubId = clubId.value,
                json = clubJson.toString()
              )
            )
          )
          .asRight[GameException]
          .pure

    }

}
