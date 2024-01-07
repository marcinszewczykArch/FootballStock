package game.events.memory

import cats.effect._
import cats.implicits.toFunctorOps
import game.errors.GameException
import game.errors.GameException.UserNotFoundException
import game.events.Event

trait EventMemory[F[_]] {

  def sendEvent(event: Event): F[Unit]
  def getEventsForUser(user: String): F[Either[GameException, List[Event]]]

}

object EventMemory {

  def impl[F[_]](
    ref: Ref[F, List[Event]]
  )(
    implicit F: Sync[F]
  ): EventMemory[F] =
    new EventMemory[F] {
      override def sendEvent(event: Event): F[Unit] = ref.update(_ :+ event)

      override def getEventsForUser(user: String): F[Either[GameException, List[Event]]] = ref
        .get
        .map(_.filter(_.user == user) match {
          case Nil                         => Left(UserNotFoundException(user))
          case userEvents: List[Event] => Right(userEvents)
        })

    }

}
