package http.event

import cats.Applicative
import cats.effect.Async
import http.event.EventEndpoints.getEvents
import http.player.PlayerEndpoints.{getPlayer, getPlayerSearch}
import http.security.{RoleSelection, Roles, SecuredEndpoints, TokenVerification}
import org.http4s.HttpRoutes
import org.typelevel.log4cats.LoggerFactory
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter

class EventRoutes[F[_]: Async: LoggerFactory](
  tokenIntrospection: TokenVerification[F]
) extends SecuredEndpoints(tokenIntrospection) {

  def routes(logic: EventLogic[F]): F[HttpRoutes[F]] = Applicative[F].pure {

    val getEventsEndpoint: ServerEndpoint[Any, F] = getEvents
      .endpointSecured(RoleSelection.Any(Roles.Admin, Roles.User))
      .withServerLogic(logic.getEvents)


    Http4sServerInterpreter[F]()
      .toRoutes(
        List(
          getEventsEndpoint
        )
      )
  }

}
