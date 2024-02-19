package game.player.client

import cats.{Applicative, MonadThrow}
import cats.effect._
import cats.implicits.toFunctorOps
import config.AppConfig.PlayerStatsClientConfig
import game.GameException.PlayerStatsClientException
import game.player.client.domain.FetchedPlayerStats
import game.player.service.domain.PlayerId
import org.typelevel.log4cats.LoggerFactory
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri
import utils.Cache

//https://github.com/felipeall/transfermarkt-api
trait PlayerStatsClient[F[_]] {
  def fetchRawPlayerStatsById(id: PlayerId): F[FetchedPlayerStats]
}

object PlayerStatsClient {

  def cachedInstance[F[_]: Sync: LoggerFactory](
    config: PlayerStatsClientConfig,
    underlying: PlayerStatsClient[F] //PlayerStatsClient.impl[F](config)
  ): PlayerStatsClient[F] = {

    val fetchRawPlayersStatsCache: Cache[F, PlayerId, FetchedPlayerStats] =
      Cache.instance[F, PlayerId, FetchedPlayerStats](
        cacheName = config.cacheName
      )(
        lookup = playerId => underlying.fetchRawPlayerStatsById(playerId)
      )(
        ttl = config.cacheTtl,
        failedFetchTtl = config.failedCacheTtl
      )

    new PlayerStatsClient[F] {
      override def fetchRawPlayerStatsById(id: PlayerId): F[FetchedPlayerStats] = fetchRawPlayersStatsCache.get(id)
    }
  }

  def impl[F[_]: Sync: MonadThrow](config: PlayerStatsClientConfig) = new PlayerStatsClient[F] {
    val serviceUri: Uri                     = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def fetchRawPlayerStatsById(id: PlayerId): F[FetchedPlayerStats] =
      for {
        res <- backend
                 .send {
                   basicRequest
                     .get(serviceUri.addPath("players", id.value.toString, "stats"))
                     .response(asJson[FetchedPlayerStats])
                 }
                 .map(_.body) match {
                 case Right(fetchedPlayerStats) => Applicative[F].pure(fetchedPlayerStats)
                 case Left(cause)               => MonadThrow[F].raiseError[FetchedPlayerStats](PlayerStatsClientException(cause.getMessage))
               }
      } yield res

  }

}
