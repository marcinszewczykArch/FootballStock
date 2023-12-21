package httpClient

import cats.Applicative
import cats.effect._
import cats.syntax.all._
import config.AppConfig.TransfermarktClientConfig
import httpClient.domain.{FetchedMarketValue, FetchedPlayerProfile, FetchedPlayerSimple, PlayerSearchResponse}
import io.circe
import services.domain.PlayerId
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri

//https://github.com/felipeall/transfermarkt-api
trait TransfermarktClient[F[_]] {
  def searchByName(playerName: String): F[Either[ResponseException[String, circe.Error], List[FetchedPlayerSimple]]]
  def fetchMarketValueByPlayerId(id: PlayerId): F[Either[ResponseException[String, circe.Error], FetchedMarketValue]]
  def fetchPlayerProfileById(id: PlayerId): F[Either[ResponseException[String, circe.Error], FetchedPlayerProfile]]
}

object TransfermarktClient {

  def impl[F[_]: Sync](config: TransfermarktClientConfig) = new TransfermarktClient[F] {
    val serviceUri: Uri = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def searchByName(playerName: String): F[Either[ResponseException[String, circe.Error], List[FetchedPlayerSimple]]] =
      for {
        res <- backend
                 .send {
                   basicRequest
                     .get(serviceUri.addPath("players", "search", playerName))
                     .response(asJson[PlayerSearchResponse])
                 }
                 .map(_.body)
                 .map(_.result) match {
                 case Left(error)                 => Applicative[F].pure(Left(error))
                 case Right(fetchedPlayersSimple) => Applicative[F].pure(Right(fetchedPlayersSimple))
               }
      } yield res

    override def fetchMarketValueByPlayerId(id: PlayerId): F[Either[ResponseException[String, circe.Error], FetchedMarketValue]] = for {
      res <- backend
               .send {
                 basicRequest
                   .get(serviceUri.addPath("players", id.value.toString, "market_value"))
                   .response(asJson[FetchedMarketValue])
               }
               .map(_.body) match {
               case Left(error)        => Applicative[F].pure(Left(error))
               case Right(marketValue) => Applicative[F].pure(Right(marketValue))
             }
    } yield res

    override def fetchPlayerProfileById(id: PlayerId): F[Either[
      ResponseException[String, circe.Error],
      FetchedPlayerProfile
    ]] = for {
      res <- backend
               .send {
                 basicRequest
                   .get(serviceUri.addPath("players", id.value.toString, "profile"))
                   .response(asJson[FetchedPlayerProfile])
               }
               .map(_.body) match {
               case Left(error)                 => Applicative[F].pure(Left(error))
               case Right(fetchedPlayerProfile) => Applicative[F].pure(Right(fetchedPlayerProfile))
             }
    } yield res

  }

}
