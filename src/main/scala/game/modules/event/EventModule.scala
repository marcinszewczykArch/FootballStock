package game.modules.event

import cats.effect._
import game.modules.event.memory.EventMemory
import game.modules.event.service.EventService
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
