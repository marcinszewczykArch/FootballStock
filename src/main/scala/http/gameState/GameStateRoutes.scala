package http.gameState

import cats.effect.Async
import GameStateEndpoints._
import cats.Applicative
import http.security.TokenVerification
import http.security.SecuredEndpoints
import http.security.Roles
import http.security.RoleSelection
import org.http4s.HttpRoutes
import org.typelevel.log4cats.LoggerFactory
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class GameStateRoutes[F[_]: Async: LoggerFactory](
  tokenIntrospection: TokenVerification[F]
) extends SecuredEndpoints(tokenIntrospection) {

  def routes(logic: GameStateLogic[F]): F[HttpRoutes[F]] = Applicative[F].pure {

    val getUserGameStateServerEndpoint: ServerEndpoint[Any, F] = getUserGameState
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogicSuccess(logic.getStateByUserId)

    Http4sServerInterpreter[F]()
      .toRoutes(
        List(
          getUserGameStateServerEndpoint
        )
      )
  }
  }


