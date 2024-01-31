package game.player.client

import cats.{Applicative, MonadThrow}
import cats.effect._
import config.AppConfig.PlayerProfileClientConfig
import game.errors.GameException
import game.errors.GameException.{PlayerMarketValueHistoryClientException, PlayerProfileClientException}
import game.player.service.domain.PlayerId
import io.circe.{Json, parser}
import sttp.client3._
import sttp.model.Uri

//https://github.com/felipeall/transfermarkt-api
trait PlayerMarketValueHistoryClient[F[_]] {
  def fetchRawMarketValueHistoryById(id: PlayerId): F[Either[GameException, Json]]
}

object PlayerMarketValueHistoryClient {
//todo: edit config to add new Client Config
  def impl[F[_]: Sync: MonadThrow](config: PlayerProfileClientConfig) = new PlayerMarketValueHistoryClient[F] {
    val serviceUri: Uri                     = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def fetchRawMarketValueHistoryById(id: PlayerId): F[Either[GameException, Json]] = Applicative[F].pure(for {
      jsonRes <- backend.send {
                   basicRequest
                     .get(serviceUri.addPath("players", id.value.toString, "market_value"))
                 }.body match {
                   case Right(strJson) =>
                     parser.parse(strJson) match {
                       case Right(json)          => Right(json)
                       case Left(parsingFailure) => Left(PlayerMarketValueHistoryClientException(parsingFailure.getMessage()))
                     }
                   case Left(cause)    => Left(PlayerMarketValueHistoryClientException(cause))
                 }
    } yield jsonRes)

  }

}
