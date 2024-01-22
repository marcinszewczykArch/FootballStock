package http.event

import cats.data.EitherT
import cats.effect.Sync
import cats.implicits.toFunctorOps
import game.errors.GameException
import game.logic.GameEngine
import game.player.service.domain.PlayerId
import game.state.domain.User
import http.event.domain.EventsResponse
import http.gameState.domain._
import http.player.domain.PlayerProfileResponse
import http.player.domain.PlayerSearchResponse
import http.player.domain.PlayerSimpleResponse
import org.typelevel.log4cats.LoggerFactory

trait EventLogic[F[_]] {
  def getEvents(user: String): F[Either[GameException, EventsResponse]]
}

object EventLogic {

  def impl[F[_]: Sync: LoggerFactory](
    gameEngine: GameEngine[F]
  ) = new EventLogic[F] {

    override def getEvents(user: String): F[Either[GameException, EventsResponse]] = gameEngine
      .getUserEvents(User(user))
      .map(_.map(EventsResponse(_)))

  }

}
