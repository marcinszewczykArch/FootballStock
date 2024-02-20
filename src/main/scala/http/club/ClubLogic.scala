package http.club

import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.GameEngine
import game.modules.club.service.domain.ClubId
import game.modules.player.service.domain.PlayerId
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

    override def getClubProfile(clubId: Int): F[Either[GameExceptionResponse, ClubProfileResponse]] = gameEngine
      .getClubProfileById(ClubId(clubId))
      .map(
        _.map(domain.ClubProfileResponse.fromDomainClubProfile)
          .left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

    override def getClubSearch(clubName: String): F[Either[GameExceptionResponse, ClubSearchResponse]] = gameEngine
      .searchClubByName(clubName)
      .map(
        _.map(domain.ClubSearchResponse.fromDomainClubSimpleList)
          .left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

    override def getClubPlayers(clubId: Int): F[Either[GameExceptionResponse, ClubPlayersResponse]] = gameEngine
      .getClubPlayersById(ClubId(clubId))
      .map(
        _.map(domain.ClubPlayersResponse.fromDomainClubPlayers)
          .left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

  }

}
