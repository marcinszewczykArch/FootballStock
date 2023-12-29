package game.memory

import cats.effect._
import cats.implicits.toFunctorOps
import game.errors.GameException
import game.errors.GameException.UserNotFoundException
import game.events.UserEvent

trait EventMemory[F[_]] {

  def sendEvent(event: UserEvent): F[Unit]
  def getEventsForPlayer(user: String): F[Either[GameException, List[UserEvent]]]

}

object EventMemory {

  def impl[F[_]](
    ref: Ref[F, List[UserEvent]]
  )(
    implicit F: Sync[F]
  ): EventMemory[F] =
    new EventMemory[F] {
      override def sendEvent(event: UserEvent): F[Unit] = ref.update(_ :+ event)

      override def getEventsForPlayer(user: String): F[Either[GameException, List[UserEvent]]] = ref
        .get
        .map(_.filter(_.user == user) match {
          case Nil                         => Left(UserNotFoundException(user))
          case userEvents: List[UserEvent] => Right(userEvents)
        })

    }

}
