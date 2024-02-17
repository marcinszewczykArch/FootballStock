package game

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.toTraverseOps
import game.GameException.{NotEnoughMoneyException, PlayerMarketValueNotUpToDateException}
import game.club.service.ClubService
import game.club.service.domain.{ClubId, ClubPlayers, ClubProfile, ClubSimple}
import game.event.Event
import game.event.Event.{BuyPlayerEvent, InitializeGameEvent, SellPlayerEvent}
import game.event.service.EventService
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerService
import game.player.service.domain._
import game.state.domain._
import game.state.service.UserGameStateService
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.TimeProvider
import utils.Type.ErrorOr

trait GameEngine[F[_]] {

  def buyPlayer(user: User)(playerId: PlayerId, sharesToBuy: Int): F[ErrorOr[BuyPlayerEvent]]
  def sellPlayer(user: User)(playerId: PlayerId, sharesToSell: Int): F[ErrorOr[SellPlayerEvent]]

  def getUserState(user: User): F[ErrorOr[UserGameState]]
  def getUserBalance(user: User): F[ErrorOr[UserBalance]]

  def getAllUsersStates(): F[ErrorOr[Map[User, UserGameState]]]
  def getAllUsersBalances(): F[ErrorOr[List[UserBalance]]]

  def createUser(user: User): F[ErrorOr[InitializeGameEvent]]

  def getUserEvents(user: User): F[ErrorOr[List[Event]]]

  def addToUserWishlist(user: User)(playerId: PlayerId): F[ErrorOr[Unit]]
  def removeFromUserWishlist(user: User)(playerId: PlayerId): F[ErrorOr[Unit]]

  def searchPlayerByName(playerName: String): F[ErrorOr[List[PlayerSimple]]]
  def getMarketValueByPlayerId(id: PlayerId): F[ErrorOr[BigDecimal]]
  def getMarketValueHistoryByPlayerId(id: PlayerId): F[ErrorOr[MarketValueHistory]]
  def getPlayerProfileById(id: PlayerId): F[ErrorOr[PlayerProfile]]
  def getPlayerStatsById(id: PlayerId): F[ErrorOr[PlayerStats]]

  def searchClubByName(clubName: String): F[ErrorOr[List[ClubSimple]]]
  def getClubProfileById(id: ClubId): F[ErrorOr[ClubProfile]]
  def getClubPlayersById(id: ClubId): F[ErrorOr[ClubPlayers]]

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
      ): F[ErrorOr[BuyPlayerEvent]] = (for {
        _                    <- EitherT.liftF(log.debug(s"Processing new transaction for user $user: BUY $sharesToBuy of $playerId..."))
        now                  <- EitherT.pure(timeProvider.getCurrentTimestamp)
        userState            <- EitherT(stateService.getStateForUser(user))
        playerDisplayedValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
        player               <- EitherT(playerService.updateAndGetPlayerProfileById(playerId))
        playerStats          <- EitherT(playerService.getPlayerStatsById(playerId))
        _                    <- EitherT.fromEither[F](validateDisplayedValueIsValid(playerDisplayedValue, player))
        newShares            <- EitherT(
                                  stateService.calculateSharesAfterBuy(
                                    sharesInPortfolio = userState.portfolio.get(playerId).map(_.shares),
                                    sharesToBuy = sharesToBuy,
                                    currentPlayerMarketValue = player.marketValue,
                                    buyMinutesPlayed = playerStats.totalMinutesPlayed,
                                    minutesPlayedLastSeen = playerStats.totalMinutesPlayed,
                                    dividend = 0
                                  )
                                )
        transactionValue = player.marketValue * sharesToBuy / 100
        _ <- EitherT(validateEnoughMoney(userState.money, transactionValue))
        event        = BuyPlayerEvent(playerId, player.name, sharesToBuy, transactionValue, user, now)
        newUserState = UserGameState(
                         user = user,
                         portfolio = userState.portfolio + (playerId -> StockInfo(
                           playerId,
                           newShares,
                           player.marketValue,
                           playerStats.totalMinutesPlayed
                         )),
                         wishlist = userState.wishlist,
                         money = userState.money - transactionValue,
                         updatedAt = now
                       )
        _ <- EitherT(stateService.updateGameStateFroUser(user)(newUserState)(versionNumber = userState.updatedAt))
        _ <- EitherT.liftF[F, GameException, Unit](eventService.sendEvent(event))
      } yield event).value

      def validateDisplayedValueIsValid(
        displayedPlayerValue: BigDecimal,
        updatedPlayerProfile: PlayerProfile
      ): ErrorOr[Unit] =
        displayedPlayerValue equals updatedPlayerProfile.marketValue match {
          case false =>
            Left[GameException, Unit](
              PlayerMarketValueNotUpToDateException(updatedPlayerProfile.id, displayedPlayerValue, updatedPlayerProfile.marketValue)
            )
          case true  => Right[GameException, Unit](())
        }

      override def sellPlayer(user: User)(playerId: PlayerId, sharesToSell: Int): F[ErrorOr[SellPlayerEvent]] =
        (for {
          _                    <- EitherT.liftF(log.debug(s"Processing new transaction for user $user: SELL $sharesToSell of $playerId..."))
          now                  <- EitherT.pure(timeProvider.getCurrentTimestamp)
          userState            <- EitherT(stateService.getStateForUser(user))
          playerDisplayedValue <- EitherT(playerService.getMarketValueByPlayerId(playerId))
          player               <- EitherT(playerService.updateAndGetPlayerProfileById(playerId))
          playerStats          <- EitherT(playerService.getPlayerStatsById(playerId))
          _                    <- EitherT.fromEither[F](validateDisplayedValueIsValid(playerDisplayedValue, player))
          newShares            <- EitherT(stateService.calculateSharesAfterSell(userState.portfolio.get(playerId).map(_.shares), sharesToSell))
          transactionValue = player.marketValue * sharesToSell / 100
          event            = SellPlayerEvent(playerId, player.name, sharesToSell, transactionValue, user, now)
          newUserState     = UserGameState(
                               user = user,
                               portfolio = newShares match {
                                 case Nil => userState.portfolio - playerId
                                 case _   =>
                                   userState.portfolio + (playerId -> StockInfo(
                                     playerId,
                                     newShares,
                                     player.marketValue,
                                     playerStats.totalMinutesPlayed
                                   ))
                               },
                               wishlist = userState.wishlist,
                               money = userState.money + transactionValue,
                               updatedAt = now
                             )
          _ <- EitherT(stateService.updateGameStateFroUser(user)(newUserState)(versionNumber = userState.updatedAt))
          _ <- EitherT.liftF[F, GameException, Unit](eventService.sendEvent(event))
        } yield event).value

      private def validateEnoughMoney(available: BigDecimal, required: BigDecimal): F[ErrorOr[Unit]] =
        Applicative[F].pure(
          available >= required match {
            case true  => Right(())
            case false => Left(NotEnoughMoneyException(available, required))
          }
        )

      override def getUserState(
        user: User
      ): F[ErrorOr[UserGameState]] = stateService.getStateForUser(user)

      override def createUser(
        user: User
      ): F[ErrorOr[InitializeGameEvent]] = (for {
        _           <- EitherT.liftF(log.info(s"Start creating new USER $user..."))
        now         <- EitherT.pure(timeProvider.getCurrentTimestamp)
        _           <- EitherT(stateService.validateUserNotExists(user))
        initialCash <- EitherT.pure(UserGameState.initialCash)
        portfolio   <- EitherT.pure(Map.empty[PlayerId, StockInfo])
        event       <- EitherT.pure(InitializeGameEvent(initialCash, user, now))
        wishlist = Nil
        initialState <- EitherT.pure(UserGameState(user, portfolio, initialCash, now, wishlist))
        _            <- EitherT(stateService.saveGameStateFroUser(user)(initialState))
        _            <- EitherT.liftF[F, GameException, Unit](eventService.sendEvent(event))
      } yield event).value

      override def getUserBalance(
        user: User
      ): F[ErrorOr[UserBalance]] = (for {
        _         <- EitherT.liftF(log.debug(s"Checking balance for user: $user..."))
        userState <- EitherT(stateService.getStateForUser(user))
        balance   <- EitherT(UserBalance.fromUserState(playerService)(userState)(user))
      } yield balance).value

      override def getUserEvents(user: User): F[ErrorOr[List[Event]]] =
        eventService.getEventsForUser(user)

      override def getAllUsersStates(
      ): F[ErrorOr[Map[User, UserGameState]]] = stateService.getAllGameStates()

      override def getAllUsersBalances(
      ): F[ErrorOr[List[UserBalance]]] = (for {
        _           <- EitherT.liftF(log.debug(s"Checking balance for all users..."))
        allStates   <- EitherT(stateService.getAllGameStates())
        allBalances <-
          allStates.toList.map { case (user -> state) => EitherT(UserBalance.fromUserState(playerService)(state)(user)) }.sequence
      } yield allBalances).value

      override def searchPlayerByName(
        playerName: String
      ): F[ErrorOr[List[
        PlayerSimple
      ]]] = playerService.searchByName(playerName)

      override def getMarketValueByPlayerId(
        id: PlayerId
      ): F[ErrorOr[BigDecimal]] = playerService.getMarketValueByPlayerId(id)

      override def getPlayerProfileById(
        id: PlayerId
      ): F[ErrorOr[PlayerProfile]] = playerService.getPlayerProfileById(id)

      override def getMarketValueHistoryByPlayerId(
        id: PlayerId
      ): F[ErrorOr[MarketValueHistory]] = playerService.getMarketValueHistoryById(id)

      override def getPlayerStatsById(
        id: PlayerId
      ): F[ErrorOr[PlayerStats]] = playerService.getPlayerStatsById(id)

      override def searchClubByName(
        clubName: String
      ): F[ErrorOr[List[
        ClubSimple
      ]]] = clubService.searchByName(clubName)

      override def getClubProfileById(
        id: ClubId
      ): F[ErrorOr[ClubProfile]] = clubService.getClubProfileById(id)

      override def getClubPlayersById(
        id: ClubId
      ): F[ErrorOr[ClubPlayers]] = clubService.getClubPlayersById(id)

      override def addToUserWishlist(
        user: User
      )(
        playerId: PlayerId
      ): F[ErrorOr[Unit]] = (for {
        _         <- EitherT.liftF(log.debug(s"Adding $playerId to wishlist for $user..."))
        now       <- EitherT.pure(timeProvider.getCurrentTimestamp)
        userState <- EitherT(stateService.getStateForUser(user))
        newUserState = UserGameState(
                         user = user,
                         portfolio = userState.portfolio,
                         wishlist = userState.wishlist :+ (playerId, now),
                         money = userState.money,
                         updatedAt = now
                       )
        _ <- EitherT(stateService.updateGameStateFroUser(user)(newUserState)(versionNumber = userState.updatedAt))
      } yield ()).value

      override def removeFromUserWishlist(
        user: User
      )(
        playerId: PlayerId
      ): F[ErrorOr[Unit]] = (for {
        _         <- EitherT.liftF(log.debug(s"Removing $playerId from wishlist for $user..."))
        now       <- EitherT.pure(timeProvider.getCurrentTimestamp)
        userState <- EitherT(stateService.getStateForUser(user))
        newUserState = UserGameState(
                         user = user,
                         portfolio = userState.portfolio,
                         wishlist = userState.wishlist.filterNot(_._1 == playerId),
                         money = userState.money,
                         updatedAt = now
                       )
        _ <- EitherT(stateService.updateGameStateFroUser(user)(newUserState)(versionNumber = userState.updatedAt))
      } yield ()).value

    }

}
