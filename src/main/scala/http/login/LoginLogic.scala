package http.login

import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.GameEngine
import game.modules.state.domain.User
import http.GameExceptionResponse
import http.login.domain.LoginRequest
import org.typelevel.log4cats.LoggerFactory

trait LoginLogic[F[_]] {
  def login(request: LoginRequest): F[Either[GameExceptionResponse, Boolean]]
}

object LoginLogic {

  def impl[F[_]: Sync: LoggerFactory](
    gameEngine: GameEngine[F]
  ) = new LoginLogic[F] {

    override def login(request: LoginRequest): F[Either[GameExceptionResponse, Boolean]] = gameEngine
      .login(User(request.user))(request.password)
      .map {
        case Right(true) => Right(true)
        case _           => Left(GameExceptionResponse("Incorrect login or password"))

      }

  }

}
