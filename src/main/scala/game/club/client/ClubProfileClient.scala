package game.club.client

import cats.Applicative
import cats.MonadThrow
import cats.effect._
import config.AppConfig.ClubProfileClientConfig
import game.club.service.domain.ClubId
import game.errors.GameException
import game.errors.GameException.ClubProfileClientException
import io.circe.Json
import io.circe.parser
import sttp.client3._
import sttp.model.Uri

//https://github.com/felipeall/transfermarkt-api
trait ClubProfileClient[F[_]] {
  def fetchRawClubProfileById(id: ClubId): F[Either[GameException, Json]]
}

object ClubProfileClient {

  def impl[F[_]: Sync: MonadThrow](config: ClubProfileClientConfig) = new ClubProfileClient[F] {
    val serviceUri: Uri                     = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def fetchRawClubProfileById(id: ClubId): F[Either[GameException, Json]] = Applicative[F].pure(for {
      jsonRes <- backend.send {
                   basicRequest
                     .get(serviceUri.addPath("clubs", id.value.toString, "profile"))
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
