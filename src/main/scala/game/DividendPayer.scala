package game

import cats.Applicative
import cats.data.EitherT
import cats.effect.{Async, Ref}
import cats.implicits.{toFunctorFilterOps, toFunctorOps}
import config.AppConfig
import game.modules.event.Event.UserDividendPayedEvent
import game.modules.event.service.EventService
import game.modules.player.service.PlayerService
import game.modules.player.service.domain.PlayerId
import game.modules.state.domain.{Shares, StockInfo, User, UserGameState}
import game.modules.state.service.UserGameStateService
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.TimeProvider

import java.time.Instant

trait DividendPayer[F[_]] {
  def payDividendToAllUsers: F[Unit]
}

object DividendPayer {

  def impl[F[_]: Async: LoggerFactory](
    playerService: PlayerService[F],
    eventService: EventService[F],
    userGameStateService: UserGameStateService[F],
    appConfig: AppConfig
  )(
    implicit timeProvider: TimeProvider[F]
  ) = new DividendPayer[F] {
    val maxConcurrent                              = 8
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[DividendPayer[F]].getName)

    import cats.implicits.toTraverseOps

    override def payDividendToAllUsers: F[Unit] = (for {
      _ <- EitherT.liftF[F, GameException, Unit](log.info("Starting paying dividend to all users..."))
      now = timeProvider.getCurrentTimestamp
      allStates <- EitherT(userGameStateService.getAllGameStates()) //todo: fetch only list of users without the state

      maybeEvents <- allStates.toList.traverse { case (user, _) => updateState(user, now) }
      //todo: repeat updateState() if failed
      //todo: make it concurrent in stream (parEvalMapUnordered)

      (all, somes, nones) = (maybeEvents.size, maybeEvents.count(_.isDefined), maybeEvents.count(_.isEmpty))
      _ <- EitherT.liftF(log.info(s"Updated states statistics: ALL [$all] | UPDATED [$somes] | NOT UPDATED [$nones]"))
//      _ <- sendUpdatedEvent() //this is for admin user
      events = maybeEvents.mapFilter(identity)
      _ <- EitherT.liftF[F, GameException, Unit](log.info(s"Sending ${events.size} UserDividendPayed events"))
      _ <- EitherT.liftF[F, GameException, List[Unit]](events.map(eventService.sendEvent).sequence)
    } yield ()).rethrowT

    private def updateState(user: User, now: Instant): EitherT[F, GameException, Option[UserDividendPayedEvent]] = for {
      oldState                                         <- EitherT(userGameStateService.getStateForUser(user))
      dividendInfoForUserRef                           <- EitherT.liftF(Ref.of[F, DividendInfoForUser](DividendInfoForUser()))
      newPortfolio                                     <- oldState.portfolio.traverse(updateStockInfo(dividendInfoForUserRef))
      DividendInfoForUser(dividendInfo, totalDividend) <- EitherT.liftF(dividendInfoForUserRef.get)
      _                                                <- EitherT.liftF(log.info(s"dividendInfo for [$user]" + dividendInfo))
      newMoney = oldState.money + totalDividend
      newState = UserGameState(user, newPortfolio, newMoney, now, oldState.wishlist)
      maybeEvent <- dividendInfo.isEmpty match {
                      case false =>
                        EitherT(userGameStateService.updateGameStateForUser(user)(newState)(oldState.updatedAt)).as(
                          Some(
                            UserDividendPayedEvent(
                              user,
                              now,
                              messages = dividendInfo.map(dps =>
                                s"${dps.playerName} [${dps.playerId.value}] - minutes: ${dps.minutes}, dividend: ${dps.dividend.toInt}"
                              )
                            )
                          )
                        )
                      case true  => EitherT.pure[F, GameException](None)
                    }
    } yield maybeEvent

    def calculateDividend(minutesPlayedDelta: Int, marketValue: BigDecimal, numberOfStock: Int, dividendYield: Double) = {
      val sharesMarketValue = marketValue * numberOfStock * 0.01
      val fullGamesPlayed = minutesPlayedDelta.toDouble / 90
      sharesMarketValue * fullGamesPlayed * dividendYield
    }

    private def updateStockInfo(
      dividendInfoForUserRef: Ref[F, DividendInfoForUser]
    )(
      stockInfo: StockInfo
    ) =
      for {
        playerStats   <- EitherT(playerService.getPlayerStatsById(stockInfo.playerId))
        playerProfile <- EitherT(playerService.getPlayerProfileById(stockInfo.playerId))
        newPlayerMinutesPlayed = playerStats.totalMinutesPlayed
        newStockInfo <-
          EitherT.liftF[F, GameException, StockInfo](
            stockInfo
              .shares
              .traverse { shares =>
                val minutesPlayedDelta = newPlayerMinutesPlayed - shares.minutesPlayedLastSeen

                minutesPlayedDelta > 0 match {
                  case true =>
                    val newDividend         = calculateDividend(
                      minutesPlayedDelta = minutesPlayedDelta,
                      marketValue = playerProfile.marketValue,
                      numberOfStock = shares.number,
                      dividendYield = appConfig.updaterTask.dividendYield
                    )
                    val newShares           = Shares(
                      number = shares.number,
                      buyPrice = shares.buyPrice,
                      buyTimestamp = shares.buyTimestamp,
                      buyMinutesPlayed = shares.buyMinutesPlayed,
                      minutesPlayedLastSeen = newPlayerMinutesPlayed,
                      dividend = shares.dividend + newDividend
                    )
                    val newDividendPerStock = NewDividendPerStock(
                      playerId = stockInfo.playerId,
                      playerName = playerProfile.name,
                      minutes = minutesPlayedDelta,
                      marketValue = playerProfile.marketValue,
                      numberOfStock = shares.number,
                      dividendYield = appConfig.updaterTask.dividendYield,
                      dividend = newDividend
                    )

                    for {
                      _ <- dividendInfoForUserRef.update { case DividendInfoForUser(dividendInfo, totalDividend) =>
                             DividendInfoForUser(
                               dividendInfo = dividendInfo :+ newDividendPerStock,
                               totalDividend = totalDividend + newDividend
                             )
                           }

                    } yield newShares

                  case false => Applicative[F].pure(shares)
                }
              }
              .map(shares =>
                StockInfo(
                  playerId = stockInfo.playerId,
                  shares = shares,
                  lastPlayerValue = stockInfo.lastPlayerValue,
                  lastPlayerMinutesPlayed = newPlayerMinutesPlayed
                )
              )
          )

      } yield newStockInfo

  }

  private case class DividendInfoForUser(
    dividendInfo: List[NewDividendPerStock] = Nil,
    totalDividend: BigDecimal = 0
  )

  private case class NewDividendPerStock(
    playerId: PlayerId,
    playerName: String,
    minutes: Int,
    marketValue: BigDecimal,
    numberOfStock: Int,
    dividendYield: Double,
    dividend: BigDecimal
  )

}
