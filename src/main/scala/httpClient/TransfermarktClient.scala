package httpClient

import cats.Applicative
import cats.effect._
import cats.syntax.all._
import config.AppConfig.TransfermarktClientConfig
import httpClient.domain.{FetchedMarketValue, PlayerSearch, PlayerSearchResponse}
import io.circe
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri

trait TransfermarktClient[F[_]] {
  def searchByName(playerName: String): F[Either[ResponseException[String, circe.Error], List[PlayerSearch]]]
  def getMarketValueByPlayerId(id: Int): F[Either[ResponseException[String, circe.Error], FetchedMarketValue]]
}

object TransfermarktClient {

  def impl[F[_]: Sync](config: TransfermarktClientConfig) = new TransfermarktClient[F] {
    val serviceUri: Uri = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def searchByName(playerName: String): F[Either[ResponseException[String, circe.Error], List[PlayerSearch]]] =
      for {
        res <- backend
                 .send {
                   basicRequest
                     .get(serviceUri.addPath("players", "search", playerName))
                     .response(asJson[PlayerSearchResponse])
                 }
                 .map(_.body)
                 .map(_.result) match {
                 case Left(error)           => Applicative[F].pure(Left(error))
                 case Right(fetchedPlayers) => Applicative[F].pure(Right(fetchedPlayers))
               }
      } yield res

    override def getMarketValueByPlayerId(id: Int): F[Either[ResponseException[String, circe.Error], FetchedMarketValue]] = for {
      res <- backend
               .send {
                 basicRequest
                   .get(serviceUri.addPath("players", id.toString, "market_value"))
                   .response(asJson[FetchedMarketValue])
               }
               .map(_.body) match {
               case Left(error)        => Applicative[F].pure(Left(error))
               case Right(marketValue) => Applicative[F].pure(Right(marketValue))
             }
    } yield res

  }

}
