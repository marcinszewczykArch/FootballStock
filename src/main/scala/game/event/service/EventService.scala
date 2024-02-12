package game.event.service

import cats.effect._
import game.GameException
import game.event.Event
import game.event.memory.EventMemory
import game.state.domain.User
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.Type.ErrorOr

trait EventService[F[_]] {

  def sendEvent(event: Event): F[Unit]
  def getEventsForUser(user: User): F[ErrorOr[List[Event]]]

}

object EventService {

  def impl[F[_]: Sync: LoggerFactory](eventMemory: EventMemory[F]) = new EventService[F] {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[EventService[F]].getName)

    override def sendEvent(event: Event): F[Unit]  = eventMemory.sendEvent(event)

    override def getEventsForUser(
      user: User
    ): F[ErrorOr[List[Event]]] = eventMemory.getEventsForUser(user)

  }

}
