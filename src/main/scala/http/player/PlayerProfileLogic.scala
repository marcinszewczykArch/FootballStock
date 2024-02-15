package http.player

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.{toFunctorOps, toTraverseOps}
import game.GameEngine
import game.club.service.domain.ClubId
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
    ): F[Either[GameExceptionResponse, PlayerStatsResponse]] = (for {
        playerStats <- EitherT(gameEngine.getPlayerStatsById(PlayerId(playerId)))
        clubs <- EitherT(playerStats.stats.traverse(stat => gameEngine.getClubProfileById(stat.clubID)).map(_.sequence))
      } yield PlayerStatsResponse.fromDomainStats(playerStats)(clubs)).value
      .map(_.left.map(ge => GameExceptionResponse(ge.getMessage)))

    }

}
