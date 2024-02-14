package game.player.client

import cats.{Applicative, MonadThrow}
import cats.effect._
import config.AppConfig.{PlayerProfileClientConfig, PlayerStatsClientConfig}
import game.GameException
import GameException.PlayerProfileClientException
import game.player.service.domain.PlayerId
import io.circe.{Json, parser}
import sttp.client3._
import sttp.model.Uri
import utils.Type.ErrorOr

//https://github.com/felipeall/transfermarkt-api
trait PlayerStatsClient[F[_]] {
  def fetchRawPlayerStatsById(id: PlayerId): F[ErrorOr[Json]]
}

object PlayerStatsClient {

  def impl[F[_]: Sync: MonadThrow](config: PlayerStatsClientConfig) = new PlayerStatsClient[F] {
    val serviceUri: Uri                     = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def fetchRawPlayerStatsById(id: PlayerId): F[ErrorOr[Json]] = Applicative[F].pure(for {
      jsonRes <- backend.send {
                   basicRequest
                     .get(serviceUri.addPath("players", id.value.toString, "stats"))
                 }.body match {
                   case Right(strJson) =>
                     parser.parse(strJson) match {
                       case Right(json)          => Right(json)
                       case Left(parsingFailure) => Left(PlayerProfileClientException(parsingFailure.getMessage()))
                     }
                   case Left(cause)    => Left(PlayerProfileClientException(cause))
                 }
    } yield jsonRes)

  }

}
