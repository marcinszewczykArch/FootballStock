package http.state

import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.GameEngine
import game.player.service.domain.PlayerId
import game.state.domain.User
import http.GameExceptionResponse
import http.state.domain._
import org.typelevel.log4cats.LoggerFactory

trait GameStateLogic[F[_]] {
  def getStateByUserId(userName: String): F[Either[GameExceptionResponse, UserGameStateResponse]]
  def getAllStates(): F[Either[GameExceptionResponse, List[UserGameStateResponse]]]
  def createNewUser(userName: String): F[Either[GameExceptionResponse, CreateNewUserResponse]]
  def buyPlayer(request: BuyPlayerRequest): F[Either[GameExceptionResponse, BuyPlayerResponse]]
  def sellPlayer(request: SellPlayerRequest): F[Either[GameExceptionResponse, SellPlayerResponse]]
  //getUserInfo
  //getUserPortfolio
  //getUserBalance
  //getUserBalancePerPlayer
}

object GameStateLogic {

  def impl[F[_]: Sync: LoggerFactory](
    gameEngine: GameEngine[F]
  ) = new GameStateLogic[F] {

    override def getStateByUserId(userName: String): F[Either[GameExceptionResponse, UserGameStateResponse]] = gameEngine
      .getUserBalance(User(userName))
      .map(
        _.map(userBalance => UserGameStateResponse.fromUserBalance(userBalance))
          .left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

    override def getAllStates(): F[Either[GameExceptionResponse, List[UserGameStateResponse]]] = gameEngine
      .getAllUsersBalances()
      .map(
          _.map(userBalances => userBalances.map(UserGameStateResponse.fromUserBalance))
            .left
            .map(ge => GameExceptionResponse(ge.getMessage))
        )

    override def createNewUser(userName: String): F[Either[GameExceptionResponse, CreateNewUserResponse]] = gameEngine
      .createUser(User(userName))
      .map(
        _.map(event => CreateNewUserResponse(event.toString))
          .left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

    override def buyPlayer(request: BuyPlayerRequest): F[Either[GameExceptionResponse, BuyPlayerResponse]] = gameEngine
      .buyPlayer(User(request.user))(PlayerId(request.playerId), request.sharesToBuy)
      .map(
        _.map(event => BuyPlayerResponse(event.toString))
          .left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

    override def sellPlayer(request: SellPlayerRequest): F[Either[GameExceptionResponse, SellPlayerResponse]] = gameEngine
      .sellPlayer(User(request.user))(PlayerId(request.playerId), request.sharesToSell)
      .map(
        _.map(event => SellPlayerResponse(event.toString))
          .left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

    }

}
