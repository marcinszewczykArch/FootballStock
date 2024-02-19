package game.player

import cats.effect._
import config.AppConfig
import game.player.client.memory.PlayerProfileClientMemory
import game.player.client.PlayerMarketValueClient
import game.player.client.PlayerProfileClient
import game.player.client.PlayerSearchClient
import game.player.client.PlayerStatsClient
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

    val playerSearchClient       = PlayerSearchClient.impl[F](appConfig.playerSearchClient)
    val playerSearchClientCached = PlayerSearchClient.cachedInstance[F](appConfig.playerSearchClient, playerSearchClient)

    val playerMarketValueClient       = PlayerMarketValueClient.impl[F](appConfig.playerMarketValueClient)
    val playerMarketValueClientCached =
      PlayerMarketValueClient.cachedInstance[F](appConfig.playerMarketValueClient, playerMarketValueClient)

    val playerStatsClient       = PlayerStatsClient.impl(appConfig.playerStatsClient)
    val playerStatsClientCached = PlayerStatsClient.cachedInstance[F](appConfig.playerStatsClient, playerStatsClient)

    override val service = PlayerService.impl[F](
      playerProfileClientMemoryCached,
      playerProfileClient,
      playerSearchClientCached,
      playerMarketValueClientCached,
      playerStatsClientCached
    )

  }

}
