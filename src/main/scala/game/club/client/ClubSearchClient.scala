package game.club.client

import cats.{Applicative, MonadThrow}
import cats.effect._
import cats.syntax.all._
import config.AppConfig.ClubSearchClientConfig
import game.club.client.domain.{ClubSearchResponse, FetchedClubSimple}
import org.typelevel.log4cats.LoggerFactory
import sttp.client3._
import sttp.client3.circe.asJson
import sttp.model.Uri
import utils.Cache

//https://github.com/felipeall/transfermarkt-api
trait ClubSearchClient[F[_]] {
  def searchByName(clubName: String): F[List[FetchedClubSimple]]
}

object ClubSearchClient {

  def cachedInstance[F[_]: Sync: LoggerFactory](
    config: ClubSearchClientConfig
  ): ClubSearchClient[F] = {
    val clubSearchClient                                                = ClubSearchClient.impl[F](config)
    val fetchClubSearchCache: Cache[F, String, List[FetchedClubSimple]] =
      Cache.instance[F, String, List[FetchedClubSimple]](
        cacheName = config.cacheName
      )(
        lookup = clubSearchClient.searchByName
      )(
        ttl = config.cacheTtl,
        failedFetchTtl = config.failedCacheTtl
      )

    new ClubSearchClient[F] {
      override def searchByName(playerName: String): F[List[FetchedClubSimple]] = fetchClubSearchCache.get(playerName)
    }
  }

  def impl[F[_]: Sync](config: ClubSearchClientConfig) = new ClubSearchClient[F] {
    val serviceUri: Uri                     = config.uri
    val backend: SttpBackend[Identity, Any] = HttpClientSyncBackend()

    override def searchByName(clubName: String): F[List[FetchedClubSimple]] =
      for {
        res <- backend
                 .send {
                   basicRequest
                     .get(serviceUri.addPath("clubs", "search", clubName))
                     .response(asJson[ClubSearchResponse])
                 }
                 .map(_.body)
                 .map(_.result) match {
                 case Right(fetchedClubsSimple) => Applicative[F].pure(fetchedClubsSimple)
                 case Left(cause)               => MonadThrow[F].raiseError[List[FetchedClubSimple]](ClubSearchClientException(cause))
               }
      } yield res

  }

  final case class ClubSearchClientException(cause: Throwable)
    extends Exception(s"Exception while invoking ClubSearchClient. Message: ${cause.getMessage}", cause)

}
