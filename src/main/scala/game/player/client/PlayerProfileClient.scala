package game.player.client

import cats.Applicative
import cats.MonadThrow
import cats.data.EitherT
import cats.effect._
import cats.syntax.all._
import config.AppConfig.PlayerProfileClientConfig
import game.errors.GameException
import game.errors.GameException.PlayerProfileClientException
import game.player.client.domain.FetchedMarketValue
import game.player.client.domain.FetchedPlayerProfile
import game.player.client.domain.FetchedPlayerSimple
import game.player.client.domain.PlayerSearchResponse
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.domain.PlayerId
import io.circe.Decoder
import io.circe.Encoder
import io.circe.Json
import io.circe.parser
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri

//https://github.com/felipeall/transfermarkt-api
trait PlayerProfileClient[F[_]] {
  def fetchRawPlayerProfileById(id: PlayerId): F[Either[GameException, Json]]
}

object PlayerProfileClient {

  def impl[F[_]: Sync: MonadThrow](config: PlayerProfileClientConfig) = new PlayerProfileClient[F] {
    val serviceUri: Uri = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def fetchRawPlayerProfileById(id: PlayerId): F[Either[GameException, Json]] = Applicative[F].pure(for {
      jsonRes <- backend.send {
                   basicRequest
                     .get(serviceUri.addPath("players", id.value.toString, "profile"))
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
