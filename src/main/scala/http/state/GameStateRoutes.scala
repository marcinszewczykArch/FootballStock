package http.state

import cats.effect.Async
import GameStateEndpoints._
import cats.Applicative
import game.modules.login.domain.UserForm
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
      .withServerLogic(logic.getStateByUserId)

    val getAllGameStateServerEndpoint: ServerEndpoint[Any, F] = getAllGameState
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(_ => logic.getAllStates())

    val createNewUserServerEndpoint: ServerEndpoint[Any, F] = createNewUser
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(userName => logic.createNewUser(UserForm(userName, "password", "email"))) //todo: send object as json

    val buyPlayerServerEndpoint: ServerEndpoint[Any, F] = buyPlayer
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(logic.buyPlayer)

    val sellPlayerServerEndpoint: ServerEndpoint[Any, F] = sellPlayer
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(logic.sellPlayer)

    val addToWishlistServerEndpoint: ServerEndpoint[Any, F] = addToWishlist
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(input => logic.addToUserWishlist(input._1)(input._2))

    val removeFromWishlistServerEndpoint: ServerEndpoint[Any, F] = removeFromWishlist
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(input => logic.removeFromUserWishlist(input._1)(input._2))

    Http4sServerInterpreter[F]()
      .toRoutes(
        List(
          getUserGameStateServerEndpoint,
          getAllGameStateServerEndpoint,
          createNewUserServerEndpoint,
          buyPlayerServerEndpoint,
          sellPlayerServerEndpoint,
          addToWishlistServerEndpoint,
          removeFromWishlistServerEndpoint
        )
      )
  }

}
