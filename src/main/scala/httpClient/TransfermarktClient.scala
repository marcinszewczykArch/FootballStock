package httpClient

import cats.Applicative
import cats.effect._
import cats.syntax.all._
import config.AppConfig.TransfermarktClientConfig
import httpClient.domain.{FetchedMarketValue, PlayerDetails, PlayerSearch, PlayerSearchResponse}
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri

trait TransfermarktClient[F[_]] {
  def searchByName(playerName: String): F[Option[List[PlayerSearch]]] //todo toEither
  def getMarketValueByPlayerId(id: Int): F[Option[FetchedMarketValue]] //todo toEither
}

object TransfermarktClient {

  def impl[F[_]: Sync](config: TransfermarktClientConfig) = new TransfermarktClient[F] {
    val serviceUri: Uri = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def searchByName(playerName: String): F[Option[List[PlayerSearch]]] =
      for {
        res <- backend
                 .send {
                   basicRequest
                     .get(serviceUri.addPath("players", "search", playerName))
                     .response(asJson[PlayerSearchResponse])
                 }
                 .map(_.body)
                 .map(_.result) match {
                 case Left(error)           =>
                   Applicative[F].pure(println(s"Error while fetching result for search '$playerName': $error")).as(None) //todo: throw error
                 case Right(fetchedPlayers) => Applicative[F].pure(println(s"Fetched player(s) for search: $playerName")).as(Some(fetchedPlayers))
               }
      } yield res

    override def getMarketValueByPlayerId(id: Int): F[Option[FetchedMarketValue]] = for {
        res <- backend
                 .send {
                   basicRequest
                     .get(serviceUri.addPath("players", id.toString, "market_value"))
                     .response(asJson[FetchedMarketValue])
                 }
                 .map(_.body) match {
                 case Left(error)           =>
                   Applicative[F]
                     .pure(println(s"Error while fetching market value for player with id '$id': $error"))
                     .as(None)
                 case Right(marketValue) =>
                   Applicative[F].pure(println(s"Fetched market value for player with id: $id")).as(Some(marketValue))
               }
      } yield res
    }

}
