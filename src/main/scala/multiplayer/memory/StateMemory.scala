package multiplayer.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.toFunctorOps
import errors.GameException._
import errors._
import multiplayer.domain.{Shares, UserGameState}
import services.PlayerService
import services.domain.MarketValue

import java.time.Instant

trait StateMemory[F[_]] {

  def getUserState(user: String): F[Either[GameException, UserGameState]]
  def getAllUsersStates(): F[List[UserGameState]]
  def updateUserState(user: String)(newUserState: UserGameState): F[Either[GameException, Unit]]

}

object StateMemory {

  def impl[F[_]](
    ref: Ref[F, Map[String, UserGameState]]
  )(
    implicit F: Sync[F]
  ): StateMemory[F] =
    new StateMemory[F] {

      def updateUserState(user: String)(newUserState: UserGameState): F[Either[GameException, Unit]] = (for {
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

      private def validateEnoughMoney(available: BigDecimal, required: BigDecimal): F[Either[GameException, Unit]] =
        Applicative[F].pure(
          available >= required match {
            case true  => Right(())
            case false => Left(NotEnoughMoneyException(available, required))
          }
        )

      private def calculateSharesAfterBuy(
        sharesInPortfolio: Option[List[Shares]],
        sharesToBuy: Int,
        currentPlayerMarketValue: MarketValue
      ): F[Either[GameException, List[Shares]]] = Applicative[F].pure {
        sharesInPortfolio.sum + sharesToBuy <= 100 match {
          case true  => Right(sharesInPortfolio |+| Shares(sharesToBuy, currentPlayerMarketValue.value, Instant.now))
          case false => Left(SharesNumberException(sharesInPortfolio.sum + sharesToBuy))
        }
      }

      private def calculateSharesAfterSell(
        sharesInPortfolio: Option[List[Shares]],
        sharesToSell: Int
      ): F[Either[GameException, List[Shares]]] = Applicative[F].pure {
        sharesInPortfolio.sum - sharesToSell >= 0 match {
          case true  => Right(sharesInPortfolio |-| sharesToSell)
          case false => Left(SharesNumberException(sharesInPortfolio.sum - sharesToSell))
        }
      }

    }

}
