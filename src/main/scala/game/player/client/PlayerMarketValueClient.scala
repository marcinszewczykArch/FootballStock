package game.player.client

import cats.{Applicative, MonadThrow}
import cats.effect._
import cats.syntax.all._
import config.AppConfig.PlayerMarketValueClientConfig
import game.GameException.PlayerMarketValueHistoryClientException
import game.player.client.domain.FetchedMarketValueHistory
import game.player.service.domain.PlayerId
import org.typelevel.log4cats.LoggerFactory
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri
import utils.Cache

//https://github.com/felipeall/transfermarkt-api
trait PlayerMarketValueClient[F[_]] {
  def fetchRawMarketValueHistoryById(id: PlayerId): F[FetchedMarketValueHistory]
}

object PlayerMarketValueClient {

  def cachedInstance[F[_]: Sync: LoggerFactory](
    config: PlayerMarketValueClientConfig,
    underlying: PlayerMarketValueClient[F] //PlayerMarketValueClient.impl[F](config)
  ): PlayerMarketValueClient[F] = {
    val fetchPlayerMarketValueCache: Cache[F, PlayerId, FetchedMarketValueHistory] =
      Cache.instance[F, PlayerId, FetchedMarketValueHistory](
        cacheName = config.cacheName
      )(
        lookup = underlying.fetchRawMarketValueHistoryById
      )(
        ttl = config.cacheTtl,
        failedFetchTtl = config.failedCacheTtl
      )

    new PlayerMarketValueClient[F] {
      override def fetchRawMarketValueHistoryById(id: PlayerId): F[FetchedMarketValueHistory] =
        fetchPlayerMarketValueCache.get(id)
    }
  }

  def impl[F[_]: Sync: MonadThrow](config: PlayerMarketValueClientConfig) = new PlayerMarketValueClient[F] {
    val serviceUri: Uri                     = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def fetchRawMarketValueHistoryById(id: PlayerId): F[FetchedMarketValueHistory] =
      for {
        res <- backend
                 .send {
                   basicRequest
                     .get(serviceUri.addPath("players", id.value.toString, "market_value"))
                     .response(asJson[FetchedMarketValueHistory])
                 }
                 .map(_.body) match {
                 case Right(fetchedMarketValueHistory) => Applicative[F].pure(fetchedMarketValueHistory)
                 case Left(cause)                      =>
                   MonadThrow[F].raiseError[FetchedMarketValueHistory](PlayerMarketValueHistoryClientException(cause.getMessage))
               }
      } yield res

  }

}
