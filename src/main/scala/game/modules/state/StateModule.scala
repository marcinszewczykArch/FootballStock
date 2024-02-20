package game.modules.state

import cats.effect._
import game.modules.state.memory.UserGameStateMemory
import game.modules.state.service.UserGameStateService
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory

trait StateModule[F[_]] {
  val service: UserGameStateService[F]
}

object StateModule {

  def impl[F[_]: Sync: LoggerFactory](
    scanamo: Scanamo
  ) = new StateModule[F] {

    val stateMemory      = UserGameStateMemory.impl[F](scanamo)
    override val service = UserGameStateService.impl[F](stateMemory)

  }

}
