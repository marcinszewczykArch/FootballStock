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
  def createNewUser(userName: String): F[CreateNewUserResponse]
  def buyPlayer(request: BuyPlayerRequest): F[BuyPlayerResponse]
  def sellPlayer(request: SellPlayerRequest): F[SellPlayerResponse]
  //getUserInfo
  //getUserPortfolio
  //getUserBalance
  //getUserBalancePerPlayer
}

object GameStateLogic {

  def impl[F[_]: Sync: LoggerFactory](
    gameEngine: GameEngine[F]
  ) = new GameStateLogic[F] {

    override def getStateByUserId(userName: String): F[Either[GameException, UserGameStateResponse]] = (for {
      userBalance <- EitherT(gameEngine.getUserBalance(User(userName)))
      userGameStateResponse = UserGameStateResponse.fromUserBalance(userBalance)
    } yield userGameStateResponse).value

    override def createNewUser(userName: String): F[CreateNewUserResponse] = gameEngine
      .createUser(User(userName))
      .map {
        case Left(value)  => CreateNewUserResponse(value.getMessage) //todo: raise error
        case Right(value) => CreateNewUserResponse(value.toString)
      }

    override def buyPlayer(request: BuyPlayerRequest): F[BuyPlayerResponse] = gameEngine
      .buyPlayer(User(request.user))(PlayerId(request.playerId), request.sharesToBuy)
      .map {
        case Left(value)  => BuyPlayerResponse(value.getMessage) //todo: raise error
        case Right(value) => BuyPlayerResponse(value.toString)
      }

    override def sellPlayer(request: SellPlayerRequest): F[SellPlayerResponse] = gameEngine
      .sellPlayer(User(request.user))(PlayerId(request.playerId), request.sharesToSell)
      .map {
        case Left(value)  => SellPlayerResponse(value.getMessage) //todo: raise error
        case Right(value) => SellPlayerResponse(value.toString)
      }

  }

}
