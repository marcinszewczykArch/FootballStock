package game.memory

import cats.data.EitherT
import cats.effect._
import cats.implicits.toFunctorOps
import game.domain.UserGameState
import game.errors.GameException
import game.errors.GameException.UserNotFoundException

trait StateMemory[F[_]] {

  def getUserState(user: String): F[Either[GameException, UserGameState]]
  def getAllUsersStates(): F[List[UserGameState]]
  def updateUserStateRegistry(user: String)(newUserState: UserGameState): F[Either[GameException, Unit]]

}

object StateMemory {

  def impl[F[_]](
    ref: Ref[F, Map[String, UserGameState]]
  )(
    implicit F: Sync[F]
  ): StateMemory[F] =
    new StateMemory[F] {

      def updateUserStateRegistry(user: String)(newUserState: UserGameState): F[Either[GameException, Unit]] = (for {
        _ <- EitherT.right[GameException](ref.update(_ + (user -> newUserState)))
      } yield ()).value

      override def getUserState(user: String): F[Either[GameException, UserGameState]] = ref
        .get
        .map(_.get(user) match {
          case Some(userStats) => Right(userStats)
          case None            => Left(UserNotFoundException(user))
        })

      override def getAllUsersStates(): F[List[UserGameState]] = ref
        .get
        .map(_.toList.map(_._2))

    }

}
