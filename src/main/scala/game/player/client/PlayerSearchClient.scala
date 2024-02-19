package game.player.client

import cats.{Applicative, MonadThrow}
import cats.effect._
import cats.syntax.all._
import config.AppConfig.PlayerSearchClientConfig
import game.GameException.PlayerSearchClientException
import game.player.client.domain.{FetchedPlayerSimple, PlayerSearchResponse}
import org.typelevel.log4cats.LoggerFactory
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri
import utils.Cache

//https://github.com/felipeall/transfermarkt-api
trait PlayerSearchClient[F[_]] {
  def searchByName(playerName: String): F[List[FetchedPlayerSimple]]
}

object PlayerSearchClient {

  def cachedInstance[F[_]: Sync: LoggerFactory](
    config: PlayerSearchClientConfig,
    underlying: PlayerSearchClient[F] //PlayerSearchClient.impl[F](config)
  ): PlayerSearchClient[F] = {
    val fetchPlayerSearchCache: Cache[F, String, List[FetchedPlayerSimple]] =
      Cache.instance[F, String, List[FetchedPlayerSimple]](
        cacheName = config.cacheName
      )(
        lookup = underlying.searchByName
      )(
        ttl = config.cacheTtl,
        failedFetchTtl = config.failedCacheTtl
      )

    new PlayerSearchClient[F] {
      override def searchByName(playerName: String): F[List[FetchedPlayerSimple]] = fetchPlayerSearchCache.get(playerName)
    }
  }

  def impl[F[_]: Sync](config: PlayerSearchClientConfig) = new PlayerSearchClient[F] {
    val serviceUri: Uri                     = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def searchByName(playerName: String): F[List[FetchedPlayerSimple]] =
      for {
        res <- backend
                 .send {
                   basicRequest
                     .get(serviceUri.addPath("players", "search", playerName))
                     .response(asJson[PlayerSearchResponse])
                 }
                 .map(_.body)
                 .map(_.result) match {
                 case Right(fetchedPlayersSimple) => Applicative[F].pure(fetchedPlayersSimple)
                 case Left(cause)                 => MonadThrow[F].raiseError[List[FetchedPlayerSimple]](PlayerSearchClientException(cause.getMessage))
               }
      } yield res

  }

}
