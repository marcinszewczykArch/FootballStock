package multiplayer.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.toFunctorOps
import errors.GameException._
import errors._
import multiplayer.Buy
import multiplayer.Sell
import multiplayer.TransactionConfirmation
import multiplayer.TransactionType
import multiplayer.UserGameState
import services.PlayerService

trait StateMemory[F[_]] {

  def buyPlayer(user: String)(playerId: Int, sharesToBuy: Double): F[Either[GameException, TransactionConfirmation]]
  def sellPlayer(user: String)(playerId: Int, sharesToSell: Double): F[Either[GameException, TransactionConfirmation]]
  def getUserState(user: String): F[Either[GameException, UserGameState]]
  def getAllUsersStates(): F[List[UserGameState]]

}

object StateMemory {

  def impl[F[_]](
    ref: Ref[F, Map[String, UserGameState]],
    playerService: PlayerService[F]
  )(
    implicit F: Sync[F]
  ): StateMemory[F] =
    new StateMemory[F] {

      override def buyPlayer(
        user: String
      )(
        playerId: Int,
        sharesToBuy: Double
      ): F[Either[GameException, TransactionConfirmation]] = (for {
        userState         <- EitherT(getUserState(user))
        playerMarketValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
        newShares         <- EitherT(calculateNewShares(userState.portfolio.get(playerId), sharesToBuy, Buy))
        transactionValue = playerMarketValue.marketValue * sharesToBuy
        _                 <- EitherT(validateEnoughMoney(userState.money, transactionValue))
        newUserState = UserGameState(
                         startTimestamp = userState.startTimestamp,
                         portfolio = userState.portfolio + (playerId -> newShares),
                         money = userState.money - transactionValue
                       )
        _                 <- EitherT.right[GameException](ref.update(state => state + (user -> newUserState)))
      } yield TransactionConfirmation(Buy, playerId, sharesToBuy, transactionValue, newUserState)).value

      override def sellPlayer(user: String)(playerId: Int, sharesToSell: Double): F[Either[GameException, TransactionConfirmation]] = (for {
        userState         <- EitherT(getUserState(user))
        newShares         <- EitherT(calculateNewShares(userState.portfolio.get(playerId), sharesToSell, Sell))
        playerMarketValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
        transactionValue = playerMarketValue.marketValue * sharesToSell
        newUserState = UserGameState(
                         startTimestamp = userState.startTimestamp,
                         portfolio = userState.portfolio + (playerId -> newShares),
                         money = userState.money + transactionValue
                       )
        _                 <- EitherT.right[GameException](ref.update(state => state + (user -> newUserState)))
      } yield TransactionConfirmation(Sell, playerId, sharesToSell, transactionValue, newUserState)).value

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

      private def calculateNewShares(
        sharesInPortfolio: Option[Double],
        transactionShares: Double,
        transactionType: TransactionType
      ): F[Either[GameException, Double]] = {
        val newShares = transactionType match {
          case Sell => sharesInPortfolio.getOrElse(0.0) - transactionShares
          case Buy  => sharesInPortfolio.getOrElse(0.0) + transactionShares
        }

        Applicative[F].pure(newShares > 1.0 || newShares < 0.0 match {
          case false => Right(newShares)
          case true  => Left(SharesNumberException(newShares))
        })

      }

    }

}
