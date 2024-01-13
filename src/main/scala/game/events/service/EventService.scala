package game.events.service

import cats.effect._
import game.errors.GameException
import game.events.Event
import game.events.memory.EventMemory
import game.state.domain.User
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}

trait EventService[F[_]] {

  def sendEvent(event: Event): F[Unit]
  def getEventsForUser(user: User): F[Either[GameException, List[Event]]]

}

object EventService {

  def impl[F[_]: Sync: LoggerFactory](eventMemory: EventMemory[F]) = new EventService[F] {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[EventService[F]].getName)

    override def sendEvent(event: Event): F[Unit]  = eventMemory.sendEvent(event)

    override def getEventsForUser(
      user: User
    ): F[Either[GameException, List[Event]]] = eventMemory.getEventsForUser(user)

  }

}
