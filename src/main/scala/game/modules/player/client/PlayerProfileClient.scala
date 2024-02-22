package game.modules.player.client

import cats.Applicative
import cats.MonadThrow
import cats.effect._
import config.AppConfig.PlayerProfileClientConfig
import game.GameException
import GameException.PlayerProfileClientException
import cats.data.EitherT
import cats.implicits.{catsSyntaxApplyOps, toFunctorOps}
import game.modules.player.service.domain.PlayerId
import io.circe.Json
import io.circe.parser
import org.typelevel.log4cats.LoggerFactory
import sttp.client3._
import sttp.model.Uri
import utils.Type.ErrorOr

//https://github.com/felipeall/transfermarkt-api
trait PlayerProfileClient[F[_]] {
  def fetchRawPlayerProfileById(id: PlayerId): F[ErrorOr[Json]]
}

object PlayerProfileClient {

  def impl[F[_]: Sync: MonadThrow](config: PlayerProfileClientConfig) = new PlayerProfileClient[F] {
    val serviceUri: Uri                     = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def fetchRawPlayerProfileById(id: PlayerId): F[ErrorOr[Json]] = Applicative[F].pure(for {
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
