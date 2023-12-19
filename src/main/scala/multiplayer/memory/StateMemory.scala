package multiplayer.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.toFunctorOps
import errors.GameException._
import errors._
import multiplayer.BuyConfirmation
import multiplayer.SellConfirmation
import multiplayer.UserGameState
import services.PlayerService
import utils.TimeProvider

trait StateMemory[F[_]] {

  def buyPlayer(user: String)(playerId: Int, sharesToBuy: Double): F[Either[GameException, BuyConfirmation]]
  def sellPlayer(user: String)(playerId: Int, sharesToSell: Double): F[Either[GameException, SellConfirmation]]
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
      ): F[Either[GameException, BuyConfirmation]] = (for {
        userState   <- EitherT(getUserState(user))
        playerMarketValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
        price = playerMarketValue.marketValue * sharesToBuy
        _           <- EitherT(validateEnoughMoney(userState.money, price))
        newShares   <- EitherT(calculateNewShares(userState.portfolio.getOrElse(playerId, 0), sharesToBuy))
        newPortfolio = userState.portfolio + (playerId -> newShares)
        newUserState = UserGameState(
                         startTimestamp = userState.startTimestamp,
                         portfolio = newPortfolio,
                         money = userState.money - price
                       )
        _           <- EitherT.right[GameException](ref.update(state => state + (user -> newUserState)))
      } yield BuyConfirmation(playerId, sharesToBuy, price, newUserState)).value

      private def validateEnoughMoney(available: BigDecimal, required: BigDecimal): F[Either[GameException, Unit]] =
        Applicative[F].pure(
          available >= required match {
            case true  => Right(())
            case false => Left(NotEnoughMoneyException(available, required))
          }
        )

      private def calculateNewShares(sharesInPortfolio: Double, sharesToBuy: Double): F[Either[GameException, Double]] = {
        val newShares = sharesInPortfolio + sharesToBuy

        Applicative[F].pure(newShares > 1.0 match {
          case false => Right(newShares)
          case true  => Left(TooManySharesException(newShares))
        })

      }

      override def sellPlayer(user: String)(playerId: Int, sharesToSell: Double): F[Either[GameException, SellConfirmation]] = ???

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
