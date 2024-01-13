package http.gameState

import cats.data.EitherT
import cats.effect.Sync
import game.state.domain.User
import game.logic.GameEngine
import http.gameState.domain.UserGameStateResponse
import org.typelevel.log4cats.LoggerFactory

trait GameStateLogic[F[_]] {
  def getStateByUserId(user: User): F[UserGameStateResponse]
}

object GameStateLogic {

  def impl[F[_]: Sync: LoggerFactory](
    gameEngine: GameEngine[F]
  ) = new GameStateLogic[F] {

    override def getStateByUserId(user: User): F[UserGameStateResponse] = (for {
      userBalance <- EitherT(gameEngine.getUserBalance(user))
      userGameStateResponse = UserGameStateResponse.fromUserBalance(userBalance)
    } yield userGameStateResponse)
      .getOrRaise(???)

  }

}
