package http.event

import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.GameEngine
import game.state.domain.User
import http.GameExceptionResponse
import http.event.domain.{EventsResponse, toEventsResponse}
import org.typelevel.log4cats.LoggerFactory

trait EventLogic[F[_]] {
  def getEvents(user: String): F[Either[GameExceptionResponse, EventsResponse]]
}

object EventLogic {

  def impl[F[_]: Sync: LoggerFactory](
    gameEngine: GameEngine[F]
  ) = new EventLogic[F] {

    override def getEvents(user: String): F[Either[GameExceptionResponse, EventsResponse]] = gameEngine
      .getUserEvents(User(user))
      .map(
        _.map(toEventsResponse)
          .left
          .map(ge => GameExceptionResponse(ge.getMessage))
      )

  }

}
