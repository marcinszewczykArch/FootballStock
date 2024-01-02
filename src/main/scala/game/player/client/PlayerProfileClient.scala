package game.player.client

import cats.Applicative
import cats.MonadThrow
import cats.data.EitherT
import cats.effect._
import cats.syntax.all._
import config.AppConfig.TransfermarktClientConfig
import game.errors.GameException
import game.memory.PlayerProfileClientMemory
import game.player.client.domain.FetchedMarketValue
import game.player.client.domain.FetchedPlayerProfile
import game.player.client.domain.FetchedPlayerSimple
import game.player.client.domain.PlayerSearchResponse
import game.player.service.domain.PlayerId
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.parser
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri
import utils.Cache

//https://github.com/felipeall/transfermarkt-api
trait PlayerProfileClient[F[_]] {
  def fetchPlayerProfileById(id: PlayerId): F[FetchedPlayerProfile]
  def fetchRawPlayerProfileById(id: PlayerId): F[Json]
}

//check cache -> fallback to PlayerClientMemory -> fallback to Transfermarkt-api
object PlayerProfileClient {

  def cachedInstance[F[_]: Sync: LoggerFactory](
    config: TransfermarktClientConfig,
    memory: PlayerProfileClientMemory[F]
  ): PlayerProfileClient[F] = {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayerProfileClient[F]].getName)

    val playersClient = PlayerProfileClient.impl[F](config)
    val fetchPlayersProfileCache: Cache[F, PlayerId, Json] =
      Cache.instance[F, PlayerId, Json](
        cacheName = config.cacheName
      )(
        lookup = playerId => memory.getPlayerJson(playerId).flatMap {
            case Right(json) =>
              log.info(s"player $playerId not found in cache. Checking memory.") *>
                Applicative[F].pure(json)
            case Left(exception)  =>
              log.info(s"player $playerId not found in memory [$exception]. Calling http client.") *>
                playersClient.fetchRawPlayerProfileById(playerId)
          }

      )(
        ttl = config.cacheTtl,
        failedFetchTtl = config.failedCacheTtl
      )

    new PlayerProfileClient[F] {
      override def fetchPlayerProfileById(id: PlayerId): F[FetchedPlayerProfile] =
        fetchRawPlayerProfileById(id).map(jsonToPlayerProfile).flatMap(_.liftTo)
      override def fetchRawPlayerProfileById(id: PlayerId): F[Json] = fetchPlayersProfileCache.get(id)
    }
  }

  def impl[F[_]: Sync: MonadThrow](config: TransfermarktClientConfig) = new PlayerProfileClient[F] {
    val serviceUri: Uri = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def fetchPlayerProfileById(id: PlayerId): F[FetchedPlayerProfile] =
      fetchRawPlayerProfileById(id).map(jsonToPlayerProfile).flatMap(_.liftTo)

    override def fetchRawPlayerProfileById(id: PlayerId): F[Json] = for {
      jsonRes <- backend.send {
                   basicRequest
                     .get(serviceUri.addPath("players", id.value.toString, "profile"))
                 }.body match {
                   case Right(strJson) =>
                     parser.parse(strJson) match {
                       case Right(json)          => Applicative[F].pure(json)
                       case Left(parsingFailure) => MonadThrow[F].raiseError(PlayerRawProfileClientException(parsingFailure.getMessage()))
                     }
                   case Left(cause)    => MonadThrow[F].raiseError(PlayerRawProfileClientException(cause))
                 }
    } yield jsonRes

  }

  private val jsonToPlayerProfile: Json => Either[Throwable, FetchedPlayerProfile] = _.as[FetchedPlayerProfile]

  final case class PlayerRawProfileClientException(cause: String)
    extends Exception(s"Exception while invoking PlayerProfileClient. Message: $cause")

}
