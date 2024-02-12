package http.club

import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.GameEngine
import game.player.service.domain.PlayerId
import http.GameExceptionResponse
import http.club.domain.{ClubPlayersResponse, ClubProfileResponse, ClubSearchResponse}
import http.player.domain.{MarketValueHistoryResponse, PlayerProfileResponse, PlayerSearchResponse}
import org.typelevel.log4cats.LoggerFactory

trait ClubLogic[F[_]] {
  def getClubProfile(clubId: Int): F[Either[GameExceptionResponse, ClubProfileResponse]]
  def getClubSearch(playerName: String): F[Either[GameExceptionResponse, ClubSearchResponse]]
  def getClubPlayers(playerId: Int): F[Either[GameExceptionResponse, ClubPlayersResponse]]
}

object ClubLogic {

  def impl[F[_]: Sync: LoggerFactory](
    gameEngine: GameEngine[F]
  ) = new ClubLogic[F] {

    override def getClubProfile(clubId: Int): F[Either[GameExceptionResponse, ClubProfileResponse]] = ???
//      gameEngine
//      .getPlayerProfileById(PlayerId(playerId))
//      .map(
//        _.map(domain.PlayerProfileResponse.fromDomainPlayerProfile)
//          .left
//          .map(ge => GameExceptionResponse(ge.getMessage))
//      )

    override def getClubSearch(playerName: String): F[Either[GameExceptionResponse, ClubSearchResponse]] = ???
//      gameEngine
//      .searchByName(playerName)
//      .map(
//        _.map(domain.PlayerSearchResponse.fromDomainPlayerSimpleList)
//          .left
//          .map(ge => GameExceptionResponse(ge.getMessage))
//      )

    override def getClubPlayers(playerId: Int): F[Either[GameExceptionResponse, ClubPlayersResponse]] = ???
//      gameEngine
//      .getMarketValueHistoryByPlayerId(PlayerId(playerId))
//      .map(
//        _.map(domain.MarketValueHistoryResponse.fromDomainMarketValueHistory)
//          .left
//          .map(ge => GameExceptionResponse(ge.getMessage))
//      )

  }

}
