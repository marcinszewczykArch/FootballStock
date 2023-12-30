package game.player.client

import cats.Applicative
import cats.MonadThrow
import cats.effect._
import cats.syntax.all._
import config.AppConfig.TransfermarktClientConfig
import game.player.client.domain.FetchedMarketValue
import game.player.client.domain.FetchedPlayerProfile
import game.player.client.domain.FetchedPlayerSimple
import game.player.client.domain.PlayerSearchResponse
import game.player.service.domain.PlayerId
import org.typelevel.log4cats.LoggerFactory
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri
import utils.Cache

//https://github.com/felipeall/transfermarkt-api
trait PlayerProfileClient[F[_]] {
  def fetchPlayerProfileById(id: PlayerId): F[FetchedPlayerProfile]
}

object PlayerProfileClient {

  def cachedInstance[F[_]: Sync: LoggerFactory](
    config: TransfermarktClientConfig
  ): PlayerProfileClient[F] = {
    val playersClient = PlayerProfileClient.impl[F](config)
    val fetchPlayersProfileCache: Cache[F, PlayerId, FetchedPlayerProfile] =
      Cache.instance[F, PlayerId, FetchedPlayerProfile](
        cacheName = config.cacheName
      )(
        lookup = playersClient.fetchPlayerProfileById
      )(
        ttl = config.cacheTtl,
        failedFetchTtl = config.failedCacheTtl
      )

    new PlayerProfileClient[F] {
      override def fetchPlayerProfileById(id: PlayerId): F[FetchedPlayerProfile] = fetchPlayersProfileCache.get(id)
    }
  }

  def impl[F[_]: Sync](config: TransfermarktClientConfig) = new PlayerProfileClient[F] {
    val serviceUri: Uri = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def fetchPlayerProfileById(id: PlayerId): F[FetchedPlayerProfile] = for {
      res <- backend
               .send {
                 basicRequest
                   .get(serviceUri.addPath("players", id.value.toString, "profile"))
                   .response(asJson[FetchedPlayerProfile])
               }
               .map(_.body) match {
               case Right(fetchedPlayerProfile) => Applicative[F].pure(fetchedPlayerProfile)
               case Left(cause)                 => MonadThrow[F].raiseError[FetchedPlayerProfile](PlayerProfileClientException(cause))
             }
    } yield res

  }

  final case class PlayerProfileClientException(cause: Throwable)
    extends Exception(s"Exception while invoking PlayerProfileClient. Message: ${cause.getMessage}", cause)

}
