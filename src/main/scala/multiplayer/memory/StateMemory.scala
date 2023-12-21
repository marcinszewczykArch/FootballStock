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
import scala.annotation.tailrec

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
        newShares         <- EitherT(calculateNewShares(userState.portfolio.get(playerId), sharesToBuy, playerMarketValue, TransactionType.Buy))
        transactionValue = playerMarketValue.value * sharesToBuy
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
          newShares         <- EitherT(calculateNewShares(userState.portfolio.get(playerId), sharesToSell, playerMarketValue, TransactionType.Sell))
          transactionValue = playerMarketValue.value * sharesToSell
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

      private def calculateNewShares(
        sharesInPortfolio: Option[List[Shares]],
        transactionSharesNumber: Int,
        currentPlayerMarketValue: MarketValue,
        transactionType: TransactionType
      ): F[Either[GameException, List[Shares]]] = Applicative[F].pure {
        val sharesAfterTransaction = transactionType match {
          case TransactionType.Sell => sharesInPortfolio.getOrElse(Nil).map(_.number).sum - transactionSharesNumber
          case TransactionType.Buy  => sharesInPortfolio.getOrElse(Nil).map(_.number).sum + transactionSharesNumber
        }

        sharesAfterTransaction > 100 || sharesAfterTransaction < 0 match {
          case false =>
            Right(transactionType match {
              case TransactionType.Buy  =>
                sharesInPortfolio.getOrElse(Nil) :+ Shares(transactionSharesNumber, currentPlayerMarketValue.value, Instant.now)
              case TransactionType.Sell => minus(sharesInPortfolio.getOrElse(Nil), transactionSharesNumber)
            })
          case true  => Left(SharesNumberException(sharesAfterTransaction))
        }
      }

      @tailrec
      private def minus(currentShares: List[Shares], sharesToMinus: Int): List[Shares] = currentShares match {
        case Nil            => Nil
        case ::(head, tail) =>
          head.number - sharesToMinus > 0 match {
            case true  => Shares(head.number - sharesToMinus, head.buyPrice, head.buyTimestamp) +: tail
            case false => minus(tail, sharesToMinus - head.number)
          }
      }

    }

}
