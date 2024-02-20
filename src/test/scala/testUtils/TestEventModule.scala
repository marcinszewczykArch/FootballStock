package testUtils

import cats.effect._
import game.GameException.UserNotFoundException
import game.modules.event.{Event, EventModule}
import game.modules.event.memory.EventMemory
import game.modules.event.service.EventService
import game.modules.state.domain.User
import org.typelevel.log4cats.LoggerFactory
import utils.Type.ErrorOr

object TestEventModule {

  def impl(
    ref: Ref[IO, List[Event]]
  )(
    implicit loggerFactory: LoggerFactory[IO]
  ): EventModule[IO] = new EventModule[IO] {

    val eventMemory      = testEventMemory(ref)
    override val service = EventService.impl[IO](eventMemory)
  }

  private def testEventMemory(ref: Ref[IO, List[Event]]) =
    new EventMemory[IO] {
      override def sendEvent(event: Event): IO[Unit] = ref.update(_ :+ event)

      override def getEventsForUser(user: User): IO[ErrorOr[List[Event]]] = ref
        .get
        .map(_.filter(_.getUser == user) match {
          case Nil                     => Left(UserNotFoundException(user))
          case userEvents: List[Event] => Right(userEvents)
        })

    }

}
