package http.player

import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.GameEngine
import game.player.service.domain.PlayerId
import http.GameExceptionResponse
import http.player.domain.{MarketValueHistoryResponse, PlayerProfileResponse, PlayerSearchResponse, PlayerStatsResponse}
import org.typelevel.log4cats.LoggerFactory

trait PlayerProfileLogic[F[_]] {
  def getPlayerProfile(playerId: Int): F[Either[GameExceptionResponse, PlayerProfileResponse]]
  def getPlayerSearch(playerName: String): F[Either[GameExceptionResponse, PlayerSearchResponse]]
  def getPlayerMarketValueHistory(playerId: Int): F[Either[GameExceptionResponse, MarketValueHistoryResponse]]
  def getPlayerStats(playerId: Int): F[Either[GameExceptionResponse, PlayerStatsResponse]]
}

object PlayerProfileLogic {

  def impl[F[_]: Sync: LoggerFactory](
    gameEngine: GameEngine[F]
  ) = new PlayerProfileLogic[F] {

    override def getPlayerProfile(playerId: Int): F[Either[GameExceptionResponse, PlayerProfileResponse]] = gameEngine
      .getPlayerProfileById(PlayerId(playerId))
      .map(
        _.map(domain.PlayerProfileResponse.fromDomainPlayerProfile)
          .left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

    override def getPlayerSearch(playerName: String): F[Either[GameExceptionResponse, PlayerSearchResponse]] = gameEngine
      .searchPlayerByName(playerName)
      .map(
        _.map(domain.PlayerSearchResponse.fromDomainPlayerSimpleList)
          .left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

    override def getPlayerMarketValueHistory(
      playerId: Int
    ): F[Either[GameExceptionResponse, MarketValueHistoryResponse]] = gameEngine
      .getMarketValueHistoryByPlayerId(PlayerId(playerId))
      .map(
        _.map(domain.MarketValueHistoryResponse.fromDomainMarketValueHistory)
          .left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

    override def getPlayerStats(
      playerId: Int
    ): F[Either[GameExceptionResponse, PlayerStatsResponse]] = gameEngine
        .getPlayerStatsById(PlayerId(playerId))
        .map(
          _.map(domain.PlayerStatsResponse.fromDomainStats)
            .left
            .map(ge => GameExceptionResponse(ge.getMessage))
        )

    }

}
