package http.login

import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.GameEngine
import game.modules.login.domain.{UserForm, UserLoginResponse}
import game.modules.state.domain.User
import http.GameExceptionResponse
import http.login.domain.{CreateUserRequest, LoginRequest}
import io.circe.syntax.EncoderOps
import org.typelevel.log4cats.LoggerFactory
import utils.Coder

trait LoginLogic[F[_]] {
  def login(request: LoginRequest): F[Either[GameExceptionResponse, String]]
  def createUser(request: CreateUserRequest): F[Either[GameExceptionResponse, String]]
}

object LoginLogic {

  def impl[F[_]: Sync: LoggerFactory](
    gameEngine: GameEngine[F]
  ) = new LoginLogic[F] {

    override def login(request: LoginRequest): F[Either[GameExceptionResponse, String]] = gameEngine
      .login(User(request.user))(request.password)
      .map {
        case Right(resp: UserLoginResponse) => Right(Coder.encodeString(resp.asJson.toString()))
        case _                              => Left(GameExceptionResponse("Incorrect login or password."))
      }

    override def createUser(
      request: CreateUserRequest
    ): F[Either[GameExceptionResponse, String]] = gameEngine
      .createNewUser(
        UserForm(
          user = request.user,
          password = request.password,
          email = request.email
        )
      )
      .map(o => o.map(_.user.value))
      .map(
        _.left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

  }

}
