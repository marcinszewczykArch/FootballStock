package game.logic

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.toTraverseOps
import game.club.service.ClubService
import game.club.service.domain.{ClubId, ClubPlayers, ClubProfile, ClubSimple}
import game.errors.GameException
import game.errors.GameException.{NotEnoughMoneyException, PlayerMarketValueNotUpToDateException}
import game.events.Event
import game.events.Event.{BuyPlayerEvent, InitializeGameEvent, SellPlayerEvent}
import game.events.service.EventService
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerService
import game.player.service.domain.{MarketValueHistory, PlayerId, PlayerProfile, PlayerSimple}
import game.state.domain._
import game.state.service.UserGameStateService
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.TimeProvider

trait GameEngine[F[_]] {

  def buyPlayer(user: User)(playerId: PlayerId, sharesToBuy: Int): F[Either[GameException, BuyPlayerEvent]]
  def sellPlayer(user: User)(playerId: PlayerId, sharesToSell: Int): F[Either[GameException, SellPlayerEvent]]

  def getUserState(user: User): F[Either[GameException, UserGameState]]
  def getUserBalance(user: User): F[Either[GameException, UserBalance]]

  def getAllUsersStates(): F[Either[GameException, Map[User, UserGameState]]]
  def getAllUsersBalances(): F[Either[GameException, List[UserBalance]]]

  def createUser(user: User): F[Either[GameException, InitializeGameEvent]]

  def getUserEvents(user: User): F[Either[GameException, List[Event]]]

  def searchPlayerByName(playerName: String): F[Either[GameException, List[PlayerSimple]]]
  def getMarketValueByPlayerId(id: PlayerId): F[Either[GameException, BigDecimal]]
  def getMarketValueHistoryByPlayerId(id: PlayerId): F[Either[GameException, MarketValueHistory]]
  def getPlayerProfileById(id: PlayerId): F[Either[GameException, PlayerProfile]]

  def searchClubByName(clubName: String): F[Either[GameException, List[ClubSimple]]]
  def getClubProfileById(id: ClubId): F[Either[GameException, ClubProfile]]
  def getClubPlayersById(id: ClubId): F[Either[GameException, ClubPlayers]]

}

object GameEngine {

  def impl[F[_]: LoggerFactory](
    stateService: UserGameStateService[F],
    eventService: EventService[F],
    playerService: PlayerService[F],
    clubService: ClubService[F]
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
        _                    <- EitherT.liftF(log.debug(s"Processing new transaction for user $user: BUY $sharesToBuy of $playerId..."))
        now                  <- EitherT.pure(timeProvider.getCurrentTimestamp)
        userState            <- EitherT(stateService.getStateForUser(user))
        playerDisplayedValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
        player               <- EitherT(playerService.updateAndGetPlayerProfileById(playerId))
        _                    <- EitherT.fromEither[F](validateDisplayedValueIsValid(playerDisplayedValue, player))
        newShares            <-
          EitherT(stateService.calculateSharesAfterBuy(userState.portfolio.get(playerId).map(_.shares), sharesToBuy, player.marketValue))
        transactionValue = player.marketValue * sharesToBuy / 100
        _ <- EitherT(validateEnoughMoney(userState.money, transactionValue))
        event        = BuyPlayerEvent(playerId, player.name, sharesToBuy, transactionValue, user, now)
        newUserState = UserGameState(
                         portfolio = userState.portfolio + (playerId -> StockInfo(newShares, player.marketValue)),
                         money = userState.money - transactionValue,
                         updatedAt = now
                       )
        _ <- EitherT(stateService.updateGameStateFroUser(user)(newUserState)(versionNumber = userState.updatedAt))
        _ <- EitherT.liftF[F, GameException, Unit](eventService.sendEvent(event))
      } yield event).value

      def validateDisplayedValueIsValid(
        displayedPlayerValue: BigDecimal,
        updatedPlayerProfile: PlayerProfile
      ): Either[GameException, Unit] =
        displayedPlayerValue equals updatedPlayerProfile.marketValue match {
          case false =>
            Left[GameException, Unit](
              PlayerMarketValueNotUpToDateException(updatedPlayerProfile.id, displayedPlayerValue, updatedPlayerProfile.marketValue)
            )
          case true  => Right[GameException, Unit](())
        }

      override def sellPlayer(user: User)(playerId: PlayerId, sharesToSell: Int): F[Either[GameException, SellPlayerEvent]] =
        (for {
          _                    <- EitherT.liftF(log.debug(s"Processing new transaction for user $user: SELL $sharesToSell of $playerId..."))
          now                  <- EitherT.pure(timeProvider.getCurrentTimestamp)
          userState            <- EitherT(stateService.getStateForUser(user))
          playerDisplayedValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
          player               <- EitherT(playerService.updateAndGetPlayerProfileById(playerId))
          _                    <- EitherT.fromEither[F](validateDisplayedValueIsValid(playerDisplayedValue, player))
          newShares            <- EitherT(stateService.calculateSharesAfterSell(userState.portfolio.get(playerId).map(_.shares), sharesToSell))
          transactionValue = player.marketValue * sharesToSell / 100
          event            = SellPlayerEvent(playerId, player.name, sharesToSell, transactionValue, user, now)
          newUserState     = UserGameState(
                               portfolio = newShares match {
                                 case Nil => userState.portfolio - playerId
                                 case _   => userState.portfolio + (playerId -> StockInfo(newShares, player.marketValue))
                               },
                               money = userState.money + transactionValue,
                               updatedAt = now
                             )
          _ <- EitherT(stateService.updateGameStateFroUser(user)(newUserState)(versionNumber = userState.updatedAt))
          _ <- EitherT.liftF[F, GameException, Unit](eventService.sendEvent(event))
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
        portfolio    <- EitherT.pure(Map.empty[PlayerId, StockInfo])
        event        <- EitherT.pure(InitializeGameEvent(initialCash, user, now))
        initialState <- EitherT.pure(UserGameState(portfolio, initialCash, now))
        _            <- EitherT(stateService.saveGameStateFroUser(user)(initialState))
        _            <- EitherT.liftF[F, GameException, Unit](eventService.sendEvent(event))
      } yield event).value

      override def getUserBalance(
        user: User
      ): F[Either[GameException, UserBalance]] = (for {
        _         <- EitherT.liftF(log.debug(s"Checking balance for user: $user..."))
        userState <- EitherT(stateService.getStateForUser(user))
        balance   <- EitherT(UserBalance.fromUserState(playerService)(userState)(user))
      } yield balance).value

      override def getUserEvents(user: User): F[Either[GameException, List[Event]]] =
        eventService.getEventsForUser(user)

      override def getAllUsersStates(
      ): F[Either[GameException, Map[User, UserGameState]]] = stateService.getAllGameStates()

      override def getAllUsersBalances(
      ): F[Either[GameException, List[UserBalance]]] = (for {
        _           <- EitherT.liftF(log.debug(s"Checking balance for all users..."))
        allStates   <- EitherT(stateService.getAllGameStates())
        allBalances <- allStates.toList.map { case (user -> state) => EitherT(UserBalance.fromUserState(playerService)(state)(user)) }.sequence
      } yield allBalances).value

      override def searchPlayerByName(
        playerName: String
      ): F[Either[GameException, List[
        PlayerSimple
      ]]] = playerService.searchByName(playerName)

      override def getMarketValueByPlayerId(
        id: PlayerId
      ): F[Either[GameException, BigDecimal]] = playerService.getMarketValueByPlayerId(id)

      override def getPlayerProfileById(
        id: PlayerId
      ): F[Either[GameException, PlayerProfile]] = playerService.getPlayerProfileById(id)

      override def getMarketValueHistoryByPlayerId(
        id: PlayerId
      ): F[Either[GameException, MarketValueHistory]] = playerService.getMarketValueHistoryById(id)

      override def searchClubByName(
        clubName: String
      ): F[Either[GameException, List[
        ClubSimple
      ]]] = clubService.searchByName(clubName)

      override def getClubProfileById(
        id: ClubId
      ): F[Either[GameException, ClubProfile]] = clubService.getClubProfileById(id)

      override def getClubPlayersById(
        id: ClubId
      ): F[Either[GameException, ClubPlayers]] = clubService.getClubPlayersById(id)

    }

}
