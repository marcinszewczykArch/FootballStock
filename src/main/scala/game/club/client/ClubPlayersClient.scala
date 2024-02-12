package game.club.client

import cats.{Applicative, MonadThrow}
import cats.effect._
import config.AppConfig.ClubPlayersClientConfig
import game.GameException
import game.club.service.domain.ClubId
import GameException.ClubProfileClientException
import io.circe.{Json, parser}
import sttp.client3._
import sttp.model.Uri
import utils.Type.ErrorOr

//https://github.com/felipeall/transfermarkt-api
trait ClubPlayersClient[F[_]] {
  def fetchRawClubPlayersById(id: ClubId): F[ErrorOr[Json]]
}

object ClubPlayersClient {

  def impl[F[_]: Sync: MonadThrow](config: ClubPlayersClientConfig) = new ClubPlayersClient[F] {
    val serviceUri: Uri                     = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def fetchRawClubPlayersById(id: ClubId): F[ErrorOr[Json]] = Applicative[F].pure(for {
      jsonRes <- backend.send {
                   basicRequest
                     .get(serviceUri.addPath("clubs", id.value.toString, "players"))
                 }.body match {
                   case Right(strJson) =>
                     parser.parse(strJson) match {
                       case Right(json)          => Right(json)
                       case Left(parsingFailure) => Left(ClubProfileClientException(parsingFailure.getMessage()))
                     }
                   case Left(cause)    => Left(ClubProfileClientException(cause))
                 }
    } yield jsonRes)

  }

}
