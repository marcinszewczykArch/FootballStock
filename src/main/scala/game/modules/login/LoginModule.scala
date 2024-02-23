package game.modules.login

import cats.effect._
import game.modules.login.memory.LoginMemory
import game.modules.login.service.LoginService
import game.modules.state.memory.UserGameStateMemory
import game.modules.state.service.UserGameStateService
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory

trait LoginModule[F[_]] {
  val service: LoginService[F]
}

object LoginModule {

  def impl[F[_]: Sync: LoggerFactory](
    scanamo: Scanamo
  ) = new LoginModule[F] {

    val loginMemory      = LoginMemory.impl[F](scanamo)
    override val service = LoginService.impl[F](loginMemory)

  }

}
