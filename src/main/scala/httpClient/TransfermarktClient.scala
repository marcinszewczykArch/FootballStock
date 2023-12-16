package httpClient

import cats.Applicative
import cats.effect._
import cats.syntax.all._
import com.softwaremill.hiring_task.AppConfig.TransfermarktClientConfig
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri

trait TransfermarktClient[F[_]] {
  def searchPlayer(playerName: String): F[List[PlayerSearch]]
//  def playerDetailsById(id: Int): F[PlayerDetails]
}

object TransfermarktClient {

  def impl[F[_]: Sync](config: TransfermarktClientConfig) = new TransfermarktClient[F] {
    val serviceUri: Uri = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def searchPlayer(playerName: String): F[List[PlayerSearch]] =
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
                   Applicative[F].pure(println(s"Error while fetching result for search '$playerName': $error")).as(List.empty) //todo: throw error
                 case Right(fetchedPlayers) => Applicative[F].pure(println(s"Fetched player(s) for search $playerName")).as(fetchedPlayers)
               }
      } yield res

  }

}
