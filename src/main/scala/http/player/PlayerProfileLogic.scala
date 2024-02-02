package http.player

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.errors.GameException
import game.logic.GameEngine
import game.player.service.domain.PlayerId
import game.state.domain.User
import http.gameState.domain._
import http.player.domain.{MarketValueHistoryResponse, PlayerProfileResponse, PlayerSearchResponse, PlayerSimpleResponse}
import org.typelevel.log4cats.LoggerFactory

trait PlayerProfileLogic[F[_]] {
  def getPlayerProfile(playerId: Int): F[Either[GameException, PlayerProfileResponse]]
  def getPlayerSearch(playerName: String): F[Either[GameException, PlayerSearchResponse]]
  def getPlayerMarketValueHistory(playerId: Int): F[Either[GameException, MarketValueHistoryResponse]]
}

object PlayerProfileLogic {

  def impl[F[_]: Sync: LoggerFactory](
    gameEngine: GameEngine[F]
  ) = new PlayerProfileLogic[F] {

    override def getPlayerProfile(playerId: Int): F[Either[GameException, PlayerProfileResponse]] = gameEngine
      .getPlayerProfileById(PlayerId(playerId))
      .map(_.map(domain.PlayerProfileResponse.fromDomainPlayerProfile))

    override def getPlayerSearch(playerName: String): F[Either[GameException, PlayerSearchResponse]] = gameEngine
      .searchByName(playerName)
      .map(_.map(domain.PlayerSearchResponse.fromDomainPlayerSimpleList))

    override def getPlayerMarketValueHistory(
      playerId: Int
    ): F[Either[GameException, MarketValueHistoryResponse]] = gameEngine
        .getMarketValueHistoryByPlayerId(PlayerId(playerId))
        .map(_.map(domain.MarketValueHistoryResponse.fromDomainMarketValueHistory))

    }

}
