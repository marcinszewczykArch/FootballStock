package http.player

import cats.Applicative
import cats.effect.Async
import http.player.PlayerEndpoints.{getPlayer, getPlayerSearch, getPlayerStats, getPlayerValue}
import http.security.{RoleSelection, Roles, SecuredEndpoints, TokenVerification}
import org.http4s.HttpRoutes
import org.typelevel.log4cats.LoggerFactory
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class PlayerProfileRoutes[F[_]: Async: LoggerFactory](
  tokenIntrospection: TokenVerification[F]
) extends SecuredEndpoints(tokenIntrospection) {

  def routes(logic: PlayerProfileLogic[F]): F[HttpRoutes[F]] = Applicative[F].pure {

    val getPlayerServerEndpoint: ServerEndpoint[Any, F] = getPlayer
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(logic.getPlayerProfile)

    val getPlayerSearchServerEndpoint: ServerEndpoint[Any, F] = getPlayerSearch
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(logic.getPlayerSearch)

    val getPlayerMarketValueHistoryServerEndpoint: ServerEndpoint[Any, F] = getPlayerValue
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(logic.getPlayerMarketValueHistory)

    val getPlayerStatsServerEndpoint: ServerEndpoint[Any, F] = getPlayerStats
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(logic.getPlayerStats)

    Http4sServerInterpreter[F]()
      .toRoutes(
        List(
          getPlayerServerEndpoint,
          getPlayerSearchServerEndpoint,
          getPlayerMarketValueHistoryServerEndpoint,
          getPlayerStatsServerEndpoint
        )
      )
  }

}
