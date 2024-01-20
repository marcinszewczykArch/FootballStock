package http.gameState

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.errors.GameException
import game.logic.GameEngine
import game.player.service.domain.PlayerId
import game.state.domain.User
import http.gameState.domain._
import org.typelevel.log4cats.LoggerFactory

trait GameStateLogic[F[_]] {
  def getStateByUserId(userName: String): F[Either[GameException, UserGameStateResponse]]
  def createNewUser(userName: String): F[Either[GameException, CreateNewUserResponse]]
  def buyPlayer(request: BuyPlayerRequest): F[Either[GameException, BuyPlayerResponse]]
  def sellPlayer(request: SellPlayerRequest): F[Either[GameException, SellPlayerResponse]]
  //getUserInfo
  //getUserPortfolio
  //getUserBalance
  //getUserBalancePerPlayer
}

object GameStateLogic {

  def impl[F[_]: Sync: LoggerFactory](
    gameEngine: GameEngine[F]
  ) = new GameStateLogic[F] {

    override def getStateByUserId(userName: String): F[Either[GameException, UserGameStateResponse]] = gameEngine
        .getUserBalance(User(userName))
        .map(_.map(userBalance => UserGameStateResponse.fromUserBalance(userBalance)))

    override def createNewUser(userName: String): F[Either[GameException, CreateNewUserResponse]] = gameEngine
      .createUser(User(userName))
      .map(_.map(event => CreateNewUserResponse(event.toString)))

    override def buyPlayer(request: BuyPlayerRequest): F[Either[GameException, BuyPlayerResponse]] = gameEngine
      .buyPlayer(User(request.user))(PlayerId(request.playerId), request.sharesToBuy)
      .map(_.map(event => BuyPlayerResponse(event.toString)))

    override def sellPlayer(request: SellPlayerRequest): F[Either[GameException, SellPlayerResponse]] = gameEngine
      .sellPlayer(User(request.user))(PlayerId(request.playerId), request.sharesToSell)
      .map(_.map(event => SellPlayerResponse(event.toString)))

  }

}
