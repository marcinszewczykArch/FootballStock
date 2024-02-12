package application

import cats.effect._
import config.AppConfig
import game.player.client.{PlayerMarketValueClient, PlayerProfileClient, PlayerSearchClient}
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerService
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory

trait PlayerModule[F[_]] {
  val service: PlayerService[F]
  val playerProfileClient: PlayerProfileClient[F]
  val playerProfileClientMemory: PlayerProfileClientMemory[F]
  val playerProfileClientMemoryCached: PlayerProfileClientMemory[F]
}

object PlayerModule {

  def impl[F[_]: Sync: LoggerFactory](
    scanamo: Scanamo,
    appConfig: AppConfig
  ) = new PlayerModule[F] {
    override val playerProfileClientMemory       = PlayerProfileClientMemory.impl[F](scanamo)
    override val playerProfileClient             = PlayerProfileClient.impl[F](appConfig.playerProfileClient)
    override val playerProfileClientMemoryCached =
      PlayerProfileClientMemory.cachedInstance[F](appConfig.playerProfileClient, playerProfileClient, playerProfileClientMemory)
    val playerSearchClient                       = PlayerSearchClient.cachedInstance[F](appConfig.playerSearchClient)
    val playerMarketValueClient                  = PlayerMarketValueClient.cachedInstance[F](appConfig.playerMarketValueClient)

    override val service = PlayerService.impl[F](
      playerProfileClientMemoryCached,
      playerProfileClient,
      playerSearchClient,
      playerMarketValueClient
    )

  }

}
