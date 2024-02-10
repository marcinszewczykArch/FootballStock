package http.club

import cats.Applicative
import cats.effect.Async
import http.club.ClubEndpoints.{getClub, getClubPlayers, getClubSearch}
import http.player.PlayerEndpoints.{getPlayer, getPlayerSearch, getPlayerValue}
import http.security.{RoleSelection, Roles, SecuredEndpoints, TokenVerification}
import org.http4s.HttpRoutes
import org.typelevel.log4cats.LoggerFactory
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class ClubRoutes[F[_]: Async: LoggerFactory](
  tokenIntrospection: TokenVerification[F]
) extends SecuredEndpoints(tokenIntrospection) {

  def routes(logic: ClubLogic[F]): F[HttpRoutes[F]] = Applicative[F].pure {

    val getClubServerEndpoint: ServerEndpoint[Any, F] = getClub
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(logic.getClubProfile)

    val getClubSearchServerEndpoint: ServerEndpoint[Any, F] = getClubSearch
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(logic.getClubSearch)

    val getClubPlayersServerEndpoint: ServerEndpoint[Any, F] = getClubPlayers
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(logic.getClubPlayers)

    Http4sServerInterpreter[F]()
      .toRoutes(
        List(
          getClubServerEndpoint,
          getClubSearchServerEndpoint,
          getClubPlayersServerEndpoint
        )
      )
  }

}
