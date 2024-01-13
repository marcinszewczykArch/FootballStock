package game.logic

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.toTraverseOps
import game.errors.GameException
import game.errors.GameException.NotEnoughMoneyException
import game.errors.GameException.SharesNumberException
import game.errors.GameException.UserAlreadyExistsException
import game.events.Event
import game.events.Event.BuyPlayerEvent
import game.events.Event.InitializeGameEvent
import game.events.Event.SellPlayerEvent
import game.events.memory.EventMemory
import game.gameState.domain
import game.gameState._
import game.gameState.domain.BalancePerPlayer
import game.gameState.domain.Shares
import game.gameState.domain.User
import game.gameState.domain.UserBalance
import game.gameState.domain.UserGameState
import game.gameState.service.UserGameStateService
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerService
import game.player.service.domain.MarketValue
import game.player.service.domain.PlayerId
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
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
    stateService: UserGameStateService[F],
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
        userState         <- EitherT(stateService.getStateForUser(user))
        playerMarketValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
        newShares         <- EitherT(stateService.calculateSharesAfterBuy(userState.portfolio.get(playerId), sharesToBuy, playerMarketValue))
        transactionValue = playerMarketValue.value * sharesToBuy / 100
        _ <- EitherT(validateEnoughMoney(userState.money, transactionValue))
        event        = BuyPlayerEvent(playerId, sharesToBuy, transactionValue, user, now)
        newUserState = UserGameState(
                         portfolio = userState.portfolio + (playerId -> newShares),
                         money = userState.money - transactionValue,
                         updatedAt = now
                       )
        _ <- EitherT(stateService.updateGameStateFroUser(user)(newUserState)(versionNumber = userState.updatedAt))
        _ <- EitherT.liftF[F, GameException, Unit](eventMemory.sendEvent(event))
      } yield event).value

      override def sellPlayer(user: User)(playerId: PlayerId, sharesToSell: Int): F[Either[GameException, SellPlayerEvent]] =
        (for {
          _                 <- EitherT.liftF(log.debug(s"Processing new transaction for user $user: SELL $sharesToSell of $playerId..."))
          now               <- EitherT.pure(timeProvider.getCurrentTimestamp)
          userState         <- EitherT(stateService.getStateForUser(user))
          playerMarketValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
          newShares         <- EitherT(stateService.calculateSharesAfterSell(userState.portfolio.get(playerId), sharesToSell))
          transactionValue = playerMarketValue.value * sharesToSell / 100
          event            = SellPlayerEvent(playerId, sharesToSell, transactionValue, user, now)
          newUserState     = UserGameState(
                               portfolio = newShares match {
                                 case Nil => userState.portfolio - playerId
                                 case _   => userState.portfolio + (playerId -> newShares)
                               },
                               money = userState.money + transactionValue,
                               updatedAt = now
                             )
          _ <- EitherT(stateService.updateGameStateFroUser(user)(newUserState)(versionNumber = userState.updatedAt))
          _ <- EitherT.liftF[F, GameException, Unit](eventMemory.sendEvent(event))
        } yield event).value

      private def validateEnoughMoney(available: BigDecimal, required: BigDecimal): F[Either[GameException, Unit]] =
        Applicative[F].pure(
          available >= required match {
            case true  => Right(())
            case false => Left(NotEnoughMoneyException(available, required))
          }
        )

      override def getUserState(
        user: User
      ): F[Either[GameException, UserGameState]] = stateService.getStateForUser(user)

      override def createUser(
        user: User
      ): F[Either[GameException, InitializeGameEvent]] = (for {
        _            <- EitherT.liftF(log.info(s"Start creating new USER $user..."))
        now          <- EitherT.pure(timeProvider.getCurrentTimestamp)
        _            <- EitherT(stateService.validateUserNotExists(user))
        initialCash  <- EitherT.pure(UserGameState.initialCash)
        portfolio    <- EitherT.pure(Map.empty[PlayerId, List[Shares]])
        event        <- EitherT.pure(InitializeGameEvent(initialCash, user, now))
        initialState <- EitherT.pure(UserGameState(portfolio, initialCash, now))
        _            <- EitherT(stateService.saveGameStateFroUser(user)(initialState))
        _            <- EitherT.liftF[F, GameException, Unit](eventMemory.sendEvent(event))
      } yield event).value

      override def getUserBalance(
        user: User
      ): F[Either[GameException, UserBalance]] = (for {
        _         <- EitherT.liftF(log.debug(s"Checking balance for user: $user..."))
        userState <- EitherT(stateService.getStateForUser(user))
        portfolio <- userState
                       .portfolio
                       .map { case playerId -> shares =>
                         for {
                           playerProfile <- EitherT(playerService.getPlayerProfileById(playerId))
                           currentPrice  <- EitherT(playerService.getMarketValueByPlayerId(playerId))
                           sharesNumber     = shares.map(_.number).sum
                           totalBuyValue    = shares.map { case Shares(number, buyPrice, _) => number * buyPrice / 100 }.sum
                           currentValue     = (currentPrice.value * sharesNumber) / 100
                           balancePerPlayer = BalancePerPlayer(
                                                shares = sharesNumber,
                                                averageBuyPrice = (totalBuyValue / sharesNumber) * 100,
                                                totalBuyValue = totalBuyValue,
                                                currentPrice = currentPrice.value,
                                                totalCurrentValue = currentValue,
                                                profit = currentValue - totalBuyValue,
                                                revenuePercent = totalBuyValue match {
                                                  case value if value == 0 => 0
                                                  case _                   => ((currentValue - totalBuyValue) / totalBuyValue).toInt * 100
                                                }
                                              )
                         } yield (playerProfile, balancePerPlayer)
                       }
                       .toList
                       .sequence

        playersCurrentValue = portfolio.map(_._2.totalCurrentValue).sum
        cash                = userState.money
        profit              = playersCurrentValue + cash - UserGameState.initialCash
        revenuePercent      = ((profit / UserGameState.initialCash) * 100).toInt
        updatedAt           = userState.updatedAt
      } yield domain.UserBalance(portfolio, playersCurrentValue, cash, profit, revenuePercent, updatedAt)).value

      override def getUserEvents(user: User): F[Either[GameException, List[Event]]] =
        eventMemory.getEventsForUser(user)

      override def getAllUsersStates(
      ): F[Map[User, UserGameState]] = stateService.getAllGameStates()
    }

}
