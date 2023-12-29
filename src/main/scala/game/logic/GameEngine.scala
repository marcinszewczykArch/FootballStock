package game.logic

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.toTraverseOps
import game.domain._
import game.errors.GameException
import game.errors.GameException.NotEnoughMoneyException
import game.errors.GameException.SharesNumberException
import game.errors.GameException.UserAlreadyExistsException
import game.events.BuyPlayerEvent
import game.events.InitializeGameEvent
import game.events.SellPlayerEvent
import game.events.UserEvent
import game.events.UserEventType
import game.memory.EventMemory
import game.memory.StateMemory
import game.player.service.PlayerService
import game.player.service.domain.MarketValue
import game.player.service.domain.PlayerId
import utils.TimeProvider

trait GameEngine[F[_]] {

  def buyPlayer(user: String)(playerId: PlayerId, sharesToBuy: Int): F[Either[GameException, BuyPlayerEvent]]
  def sellPlayer(user: String)(playerId: PlayerId, sharesToSell: Int): F[Either[GameException, SellPlayerEvent]]

  def getUserState(user: String): F[Either[GameException, UserGameState]]
  def getUserBalance(user: String): F[Either[GameException, UserBalance]]

  def getAllUsersStates(): F[List[UserGameState]]
  def createUser(user: String): F[Either[GameException, InitializeGameEvent]]

  def getUserEvents(user: String): F[Either[GameException, List[UserEvent]]]

}

object GameEngine {

  def impl[F[_]](
    stateMemory: StateMemory[F],
    eventMemory: EventMemory[F],
    playerService: PlayerService[F]
  )(
    implicit F: Sync[F],
    timeProvider: TimeProvider[F]
  ): GameEngine[F] =
    new GameEngine[F] {

      override def buyPlayer(
        user: String
      )(
        playerId: PlayerId,
        sharesToBuy: Int
      ): F[Either[GameException, BuyPlayerEvent]] = (for {
        now               <- EitherT.pure(timeProvider.getCurrentTimestamp)
        userState         <- EitherT(stateMemory.getUserState(user))
        playerMarketValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
        newShares         <- EitherT(calculateSharesAfterBuy(userState.portfolio.get(playerId), sharesToBuy, playerMarketValue))
        transactionValue = playerMarketValue.value * sharesToBuy / 100
        _                 <- EitherT(validateEnoughMoney(userState.money, transactionValue))
        event = BuyPlayerEvent(playerId, sharesToBuy, user, transactionValue, now)
        newUserState = UserGameState(
                         portfolio = userState.portfolio + (playerId -> newShares),
                         money = userState.money - transactionValue
                       )
        _                 <- EitherT(stateMemory.updateUserStateRegistry(user)(newUserState))
        _                 <- EitherT.liftF[F, GameException, Unit](eventMemory.sendEvent(event))
      } yield event).value

      override def sellPlayer(user: String)(playerId: PlayerId, sharesToSell: Int): F[Either[GameException, SellPlayerEvent]] =
        (for {
          now               <- EitherT.pure(timeProvider.getCurrentTimestamp)
          userState         <- EitherT(stateMemory.getUserState(user))
          playerMarketValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
          newShares         <- EitherT(calculateSharesAfterSell(userState.portfolio.get(playerId), sharesToSell))
          transactionValue = playerMarketValue.value * sharesToSell / 100
          event = SellPlayerEvent(playerId, sharesToSell, user, transactionValue, now)
          newUserState = UserGameState(
                           portfolio = newShares match {
                             case Nil => userState.portfolio - playerId
                             case _   => userState.portfolio + (playerId -> newShares)
                           },
                           money = userState.money + transactionValue
                         )
          _                 <- EitherT(stateMemory.updateUserStateRegistry(user)(newUserState))
          _                 <- EitherT.liftF[F, GameException, Unit](eventMemory.sendEvent(event))
        } yield event).value

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
      )(
        implicit timeProvider: TimeProvider[F]
      ): F[Either[GameException, List[Shares]]] = Applicative[F].pure {
        sharesInPortfolio.sum + sharesToBuy <= 100 match {
          case true  => Right(sharesInPortfolio |+| Shares(sharesToBuy, currentPlayerMarketValue.value, timeProvider.getCurrentTimestamp))
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

      override def getUserState(
        user: String
      ): F[Either[GameException, UserGameState]] = stateMemory.getUserState(user)

      override def getAllUsersStates(): F[List[UserGameState]] = stateMemory.getAllUsersStates()

      override def createUser(
        user: String
      ): F[Either[GameException, InitializeGameEvent]] = (for {
        now          <- EitherT.pure(timeProvider.getCurrentTimestamp)
        _            <- EitherT(validateUserNotExists(user))
        initialCash  <- EitherT.pure(UserGameState.initialCash)
        portfolio    <- EitherT.pure(Map.empty[PlayerId, List[Shares]])
        event        <- EitherT.pure(InitializeGameEvent(user, initialCash, now))
        initialState <- EitherT.pure(UserGameState(portfolio, initialCash))
        _            <- EitherT(stateMemory.updateUserStateRegistry(user)(initialState))
        _            <- EitherT.liftF[F, GameException, Unit](eventMemory.sendEvent(event))
      } yield event).value

      private def validateUserNotExists(user: String): F[Either[GameException, Unit]] = (for {
        allUsersStates <- EitherT.liftF(stateMemory.getAllUsersStates())
        _              <- EitherT.fromEither(allUsersStates.contains(user) match {
                            case true  => Left[GameException, Unit](UserAlreadyExistsException(user))
                            case false => Right[GameException, Unit](())
                          })
      } yield ()).value

      override def getUserBalance(
        user: String
      ): F[Either[GameException, UserBalance]] = (for {
        userState <- EitherT(stateMemory.getUserState(user))
        portfolio <- userState
                       .portfolio
                       .map { case playerId -> shares =>
                         for {
                           currentPrice <- EitherT(playerService.getMarketValueByPlayerId(playerId))
                           sharesNumber = shares.map(_.number).sum
                           totalBuyValue = shares.map { case Shares(number, buyPrice, _) => number * buyPrice / 100 }.sum
                           currentValue = (currentPrice.value * sharesNumber) / 100
                           balancePerPlayer = BalancePerPlayer(
                                                shares = sharesNumber,
                                                averageBuyPrice = (totalBuyValue / sharesNumber) * 100,
                                                totalBuyValue = totalBuyValue,
                                                currentPrice = currentPrice.value,
                                                totalCurrentValue = currentValue,
                                                profit = currentValue - totalBuyValue,
                                                revenuePercent = ((currentValue - totalBuyValue) / totalBuyValue).toInt * 100
                                              )
                         } yield playerId -> balancePerPlayer
                       }
                       .toList
                       .sequence
                       .map(_.toMap)

        playersCurrentValue = portfolio.map(_._2.totalCurrentValue).sum
        cash = userState.money
        profit = playersCurrentValue + cash - UserGameState.initialCash
        revenuePercent = ((profit / UserGameState.initialCash) * 100).toInt
      } yield UserBalance(portfolio, playersCurrentValue, cash, profit, revenuePercent)).value

      override def getUserEvents(user: String): F[Either[GameException, List[UserEvent]]] =
        eventMemory.getEventsForPlayer(user)

    }

}
