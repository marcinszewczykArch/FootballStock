package game.logic

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.toTraverseOps
import game.errors.GameException
import game.errors.GameException.{NotEnoughMoneyException, SharesNumberException, UserAlreadyExistsException}
import game.events.{BuyPlayerEvent, Event, InitializeGameEvent, SellPlayerEvent}
import game.events.memory.EventMemory
import game.gameState._
import game.gameState.memory.UserGameStateMemory
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerService
import game.player.service.domain.{MarketValue, PlayerId}
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.TimeProvider

trait GameEngine[F[_]] {

  def buyPlayer(user: User)(playerId: PlayerId, sharesToBuy: Int): F[Either[GameException, BuyPlayerEvent]]
  def sellPlayer(user: User)(playerId: PlayerId, sharesToSell: Int): F[Either[GameException, SellPlayerEvent]]

  def getUserState(user: User): F[Either[GameException, UserGameState]]
  def getUserBalance(user: User): F[Either[GameException, UserBalance]]

  def getAllUsersStates(): F[Map[User, UserGameState]]
  def createUser(user: User): F[Either[GameException, InitializeGameEvent]]

  def getUserEvents(user: User): F[Either[GameException, List[Event]]]

}

object GameEngine {

  def impl[F[_]: LoggerFactory](
    stateMemory: UserGameStateMemory[F],
    eventMemory: EventMemory[F],
    playerService: PlayerService[F]
  )(
    implicit F: Sync[F],
    timeProvider: TimeProvider[F]
  ): GameEngine[F] =
    new GameEngine[F] {

      implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayerProfileClientMemory[F]].getName)

      override def buyPlayer(
        user: User
      )(
        playerId: PlayerId,
        sharesToBuy: Int
      ): F[Either[GameException, BuyPlayerEvent]] = (for {
        _                 <- EitherT.liftF(log.debug(s"Processing new transaction for user $user: BUY $sharesToBuy of $playerId..."))
        now               <- EitherT.pure(timeProvider.getCurrentTimestamp)
        userState         <- EitherT(stateMemory.getByUser(user))
        playerMarketValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
        newShares         <- EitherT(calculateSharesAfterBuy(userState.portfolio.get(playerId), sharesToBuy, playerMarketValue))
        transactionValue = playerMarketValue.value * sharesToBuy / 100
        _                 <- EitherT(validateEnoughMoney(userState.money, transactionValue))
        event = BuyPlayerEvent(playerId, sharesToBuy, transactionValue, user, now)
        newUserState = UserGameState(
                         portfolio = userState.portfolio + (playerId -> newShares),
                         money = userState.money - transactionValue,
                         updatedAt = now
                       )
        _                 <- EitherT(stateMemory.update(user)(newUserState)(versionNumber = userState.updatedAt))
        _                 <- EitherT.liftF[F, GameException, Unit](eventMemory.sendEvent(event))
      } yield event).value

      override def sellPlayer(user: User)(playerId: PlayerId, sharesToSell: Int): F[Either[GameException, SellPlayerEvent]] =
        (for {
          _                 <- EitherT.liftF(log.debug(s"Processing new transaction for user $user: SELL $sharesToSell of $playerId..."))
          now               <- EitherT.pure(timeProvider.getCurrentTimestamp)
          userState         <- EitherT(stateMemory.getByUser(user))
          playerMarketValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
          newShares         <- EitherT(calculateSharesAfterSell(userState.portfolio.get(playerId), sharesToSell))
          transactionValue = playerMarketValue.value * sharesToSell / 100
          event = SellPlayerEvent(playerId, sharesToSell, transactionValue, user, now)
          newUserState = UserGameState(
                           portfolio = newShares match {
                             case Nil => userState.portfolio - playerId
                             case _   => userState.portfolio + (playerId -> newShares)
                           },
                           money = userState.money + transactionValue,
                           updatedAt = now
                         )
          _                 <- EitherT(stateMemory.update(user)(newUserState)(versionNumber = userState.updatedAt))
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
        user: User
      ): F[Either[GameException, UserGameState]] = stateMemory.getByUser(user)

      override def createUser(
        user: User
      ): F[Either[GameException, InitializeGameEvent]] = (for {
        _            <- EitherT.liftF(log.info(s"Start creating new USER $user..."))
        now          <- EitherT.pure(timeProvider.getCurrentTimestamp)
        _            <- EitherT(validateUserNotExists(user))
        initialCash  <- EitherT.pure(UserGameState.initialCash)
        portfolio    <- EitherT.pure(Map.empty[PlayerId, List[Shares]])
        event        <- EitherT.pure(InitializeGameEvent(initialCash, user, now))
        initialState <- EitherT.pure(UserGameState(portfolio, initialCash, now))
        _            <- EitherT(stateMemory.save(user)(initialState))
        _            <- EitherT.liftF[F, GameException, Unit](eventMemory.sendEvent(event))
      } yield event).value

      private def validateUserNotExists(user: User): F[Either[GameException, Unit]] = (for {
        allUsersStates <- EitherT.liftF(stateMemory.getAll())
        _              <- EitherT.fromEither(allUsersStates.contains(user) match {
                            case true  => Left[GameException, Unit](UserAlreadyExistsException(user))
                            case false => Right[GameException, Unit](())
                          })
      } yield ()).value

      override def getUserBalance(
        user: User
      ): F[Either[GameException, UserBalance]] = (for {
        _         <- EitherT.liftF(log.debug(s"Checking balance for user: $user..."))
        userState <- EitherT(stateMemory.getByUser(user))
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

      override def getUserEvents(user: User): F[Either[GameException, List[Event]]] =
        eventMemory.getEventsForUser(user)
      override def getAllUsersStates(
      ): F[Map[User, UserGameState]] = stateMemory.getAll()
    }

}
