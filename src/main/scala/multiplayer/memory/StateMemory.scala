package multiplayer.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.toFunctorOps
import errors.GameException._
import errors._
import multiplayer.domain.{Shares, TransactionConfirmation, TransactionType, UserGameState}
import services.PlayerService
import services.domain.{MarketValue, PlayerId}

import java.time.Instant

trait StateMemory[F[_]] {

  def buyPlayer(user: String)(playerId: PlayerId, sharesToBuy: Int): F[Either[GameException, TransactionConfirmation]]
  def sellPlayer(user: String)(playerId: PlayerId, sharesToSell: Int): F[Either[GameException, TransactionConfirmation]]
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
        playerId: PlayerId,
        sharesToBuy: Int
      ): F[Either[GameException, TransactionConfirmation]] = (for {
        userState         <- EitherT(getUserState(user))
        playerMarketValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
        newShares         <- EitherT(calculateSharesAfterBuy(userState.portfolio.get(playerId), sharesToBuy, playerMarketValue))
        transactionValue = playerMarketValue.value * sharesToBuy / 100
        _                 <- EitherT(validateEnoughMoney(userState.money, transactionValue))
        newUserState = UserGameState(
                         startTimestamp = userState.startTimestamp,
                         portfolio = userState.portfolio + (playerId -> newShares),
                         money = userState.money - transactionValue
                       )
        _                 <- EitherT.right[GameException](ref.update(state => state + (user -> newUserState)))
      } yield TransactionConfirmation(TransactionType.Buy, playerId, sharesToBuy, transactionValue, newUserState)).value

      override def sellPlayer(user: String)(playerId: PlayerId, sharesToSell: Int): F[Either[GameException, TransactionConfirmation]] =
        (for {
          userState         <- EitherT(getUserState(user))
          playerMarketValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
          newShares         <- EitherT(calculateSharesAfterSell(userState.portfolio.get(playerId), sharesToSell))
          transactionValue = playerMarketValue.value * sharesToSell / 100
          newUserState = UserGameState(
                           startTimestamp = userState.startTimestamp,
                           portfolio = newShares match {
                             case Nil => userState.portfolio - playerId
                             case _   => userState.portfolio + (playerId -> newShares)
                           },
                           money = userState.money + transactionValue
                         )
          _                 <- EitherT.right[GameException](ref.update(state => state + (user -> newUserState)))
        } yield TransactionConfirmation(TransactionType.Sell, playerId, sharesToSell, transactionValue, newUserState)).value

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
