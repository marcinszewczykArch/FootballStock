package game.player.client

import cats.{Applicative, MonadThrow}
import cats.effect._
import cats.syntax.all._
import config.AppConfig.TransfermarktClientConfig
import game.player.client.domain.{FetchedMarketValue, FetchedPlayerProfile, FetchedPlayerSimple, PlayerSearchResponse}
import game.player.service.domain.PlayerId
import org.typelevel.log4cats.LoggerFactory
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri
import utils.Cache

//https://github.com/felipeall/transfermarkt-api
trait TransfermarktClient[F[_]] {
  def searchByName(playerName: String): F[List[FetchedPlayerSimple]]
  def fetchMarketValueByPlayerId(id: PlayerId): F[FetchedMarketValue]
  def fetchPlayerProfileById(id: PlayerId): F[FetchedPlayerProfile]
}

object TransfermarktClient {

  def cachedInstance[F[_]: Sync: LoggerFactory](
    config: TransfermarktClientConfig
  ): TransfermarktClient[F] = {
    val playersClient = TransfermarktClient.impl[F](config)
    val fetchPlayersProfileCache: Cache[F, PlayerId, FetchedPlayerProfile] =
      Cache.instance[F, PlayerId, FetchedPlayerProfile](???)(
        lookup = playersClient.fetchPlayerProfileById
      )(
        ???,
        ???
      )

    new TransfermarktClient[F] {
      override def searchByName(playerName: String): F[List[FetchedPlayerSimple]] = playersClient.searchByName(playerName)
      override def fetchMarketValueByPlayerId(id: PlayerId): F[FetchedMarketValue] = playersClient.fetchMarketValueByPlayerId(id)
      override def fetchPlayerProfileById(id: PlayerId): F[FetchedPlayerProfile] = fetchPlayersProfileCache.get(id)
    }
  }

  def impl[F[_]: Sync](config: TransfermarktClientConfig) = new TransfermarktClient[F] {
    val serviceUri: Uri = config.uri
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
                 case Left(cause)                 => MonadThrow[F].raiseError[List[FetchedPlayerSimple]](PlayerServiceException(cause))
               }
      } yield res

    override def fetchMarketValueByPlayerId(id: PlayerId): F[FetchedMarketValue] = for {
      res <- backend
               .send {
                 basicRequest
                   .get(serviceUri.addPath("players", id.value.toString, "market_value"))
                   .response(asJson[FetchedMarketValue])
               }
               .map(_.body) match {
               case Right(marketValue) => Applicative[F].pure(marketValue)
               case Left(cause)        => MonadThrow[F].raiseError[FetchedMarketValue](PlayerServiceException(cause))
             }
    } yield res

    override def fetchPlayerProfileById(id: PlayerId): F[FetchedPlayerProfile] = for {
      res <- backend
               .send {
                 basicRequest
                   .get(serviceUri.addPath("players", id.value.toString, "profile"))
                   .response(asJson[FetchedPlayerProfile])
               }
               .map(_.body) match {
               case Right(fetchedPlayerProfile) => Applicative[F].pure(fetchedPlayerProfile)
               case Left(cause)                 => MonadThrow[F].raiseError[FetchedPlayerProfile](PlayerServiceException(cause))
             }
    } yield res

  }

  final case class PlayerServiceException(cause: Throwable)
    extends Exception(s"Exception while invoking PlayerServiceClient. Message: ${cause.getMessage}", cause)

}
