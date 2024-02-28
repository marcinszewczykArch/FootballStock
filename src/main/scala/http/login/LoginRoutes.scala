package http.login

import cats.Applicative
import cats.effect.Async
import cats.implicits.toFunctorOps
import game.modules.login.domain.UserForm
import http.GameExceptionResponse
import http.login.LoginEndpoints._
import http.security.SecuredEndpoints.ServerAppEndpoint
import http.security.errors.{BusinessFailure, Failure}
import http.security.{RoleSelection, Roles, SecuredEndpoints, TokenVerification}
import org.http4s.HttpRoutes
import org.typelevel.log4cats.LoggerFactory
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class LoginRoutes[F[_]: Async: LoggerFactory]() {

  def routes(logic: LoginLogic[F]): F[HttpRoutes[F]] = Applicative[F].pure {

    val loginServerEndpoint = login
      .serverLogic(input => logic.login(input).map(o => o.left.map[Failure[GameExceptionResponse]](BusinessFailure(_))))

    val createUserServerEndpoint = newUser
      .serverLogic(input => logic.createUser(input).map(o => o.left.map[Failure[GameExceptionResponse]](BusinessFailure(_))))


    Http4sServerInterpreter[F]()
      .toRoutes(
        List(
          loginServerEndpoint,
          createUserServerEndpoint
        )
      )
  }

}
