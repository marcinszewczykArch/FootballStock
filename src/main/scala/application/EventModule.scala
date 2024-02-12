package application

import cats.effect._
import game.events.memory.EventMemory
import game.events.service.EventService
import game.state.memory.UserGameStateMemory
import game.state.service.UserGameStateService
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory

trait EventModule[F[_]] {
  val service: EventService[F]
}

object EventModule {

  def impl[F[_]: Sync: LoggerFactory](
    scanamo: Scanamo
  ) = new EventModule[F] {

    val eventMemory      = EventMemory.impl[F](scanamo)
    override val service = EventService.impl[F](eventMemory)

  }

}
