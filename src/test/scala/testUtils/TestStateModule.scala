package testUtils

import cats.data.EitherT
import cats.effect.{IO, Ref}
import game.GameException
import game.GameException.UserNotFoundException
import game.state.StateModule
import game.state.domain.{User, UserGameState}
import game.state.memory.UserGameStateMemory
import game.state.service.UserGameStateService
import org.typelevel.log4cats.LoggerFactory
import utils.Type.ErrorOr

import java.time.Instant

object TestStateModule {

  def impl(
    ref: Ref[IO, Map[User, UserGameState]]
  )(
    implicit loggerFactory: LoggerFactory[IO]
  ): StateModule[IO] = new StateModule[IO] {

    val stateMemory      = testUserGameStateMemory(ref)
    override val service = UserGameStateService.impl[IO](stateMemory)

  }

  private def testUserGameStateMemory(
    ref: Ref[IO, Map[User, UserGameState]]
  ): UserGameStateMemory[IO] =
    new UserGameStateMemory[IO] {

      def save(user: User)(newUserState: UserGameState): IO[ErrorOr[Unit]] = (for {
        _ <- EitherT.right[GameException](ref.update(_ + (user -> newUserState)))
      } yield ()).value

      def update(user: User)(newUserState: UserGameState)(versionNumber: Instant): IO[ErrorOr[Unit]] =
        save(user)(newUserState)

      def getByUser(user: User): IO[ErrorOr[UserGameState]] = ref
        .get
        .map(_.get(user) match {
          case Some(userStats) => Right(userStats)
          case None            => Left(UserNotFoundException(user))
        })

      def getAll(): IO[ErrorOr[Map[User, UserGameState]]] = ref
        .get
        .map(Right[GameException, Map[User, UserGameState]])

    }

}
