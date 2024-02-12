package application

import cats.effect._
import config.AppConfig
import game.club.client.{ClubPlayersClient, ClubProfileClient, ClubSearchClient}
import game.club.client.memory.{ClubPlayersClientMemory, ClubProfileClientMemory}
import game.club.service.ClubService
import game.player.client.PlayerProfileClient
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerService
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory

trait ClubModule[F[_]] {
  val service: ClubService[F]
}

object ClubModule {

  def impl[F[_]: Sync: LoggerFactory](
    scanamo: Scanamo,
    appConfig: AppConfig
  ) = new ClubModule[F] {
    val clubProfileClientMemory       = ClubProfileClientMemory.impl[F](scanamo)
    val clubProfileClient             = ClubProfileClient.impl[F](appConfig.clubProfileClient)
    val clubProfileClientMemoryCached =
      ClubProfileClientMemory.cachedInstance[F](appConfig.clubProfileClient, clubProfileClient, clubProfileClientMemory)
    val clubPlayersClientMemory       = ClubPlayersClientMemory.impl[F](scanamo)
    val clubPlayersClient             = ClubPlayersClient.impl[F](appConfig.clubPlayersClient)
    val clubPlayersClientMemoryCached =
      ClubPlayersClientMemory.cachedInstance[F](appConfig.clubPlayersClient, clubPlayersClient, clubPlayersClientMemory)
    val clubSearchClient              = ClubSearchClient.cachedInstance[F](appConfig.clubSearchClient)

    override val service = ClubService.impl[F](
      clubProfileClientMemoryCached,
      clubPlayersClientMemoryCached,
      clubSearchClient
    )

  }

}
