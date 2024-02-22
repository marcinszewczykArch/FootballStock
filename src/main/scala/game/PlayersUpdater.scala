package game

import cats.Applicative
import cats.data.EitherT
import cats.effect.{Async, Ref}
import cats.effect.kernel.Clock
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxApplyOps, toFlatMapOps, toFunctorFilterOps, toFunctorOps, toTraverseOps}
import config.AppConfig.PlayersUpdateCriteriaConfig
import game.modules.event.Event.{PlayerValueChanged, PlayersUpdateEvent}
import game.modules.event.service.EventService
import game.modules.player.service.PlayerService
import game.modules.player.service.domain.PlayerId
import game.modules.state.domain
import game.modules.state.domain.{StockInfo, User, UserGameState}
import game.modules.state.service.UserGameStateService
import io.circe.Json
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.Parser.toInstantOrFarPastForUpdateAt
import utils.TimeProvider

import java.time.Instant

trait PlayersUpdater[F[_]] {
  def updateAllPlayersInMemory: F[Unit]
  def updatePlayersValueInUserStates: F[Unit]
}

object PlayersUpdater {

  def impl[F[_]: Async: LoggerFactory](
    playerService: PlayerService[F],
    eventService: EventService[F],
    userGameStateService: UserGameStateService[F],
    playersUpdateCriteria: PlayersUpdateCriteriaConfig
  )(
    implicit timeProvider: TimeProvider[F]
  ) = new PlayersUpdater[F] {
    val maxConcurrent                              = 8
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayersUpdater[F]].getName)

    override def updateAllPlayersInMemory: F[Unit] = for {
      _                            <- log.info("Starting players profile update task for all players in memory...")
      now                          <- Applicative[F].pure(timeProvider.getCurrentTimestamp)
      thresholdTimestamp           <- Applicative[F].pure(now.minusSeconds(playersUpdateCriteria.notUpdatedFor.toSeconds))
      updateStatisticRef           <- Ref.of[F, UpdateStats](UpdateStats())
      playerIdsToUpdate            <- playerService.getAllPlayerProfilesJsonDirectlyFromMemory().flatMap(filteredPlayers(thresholdTimestamp))
      (updateDuration, _)          <- Clock[F].timed(updatePlayersUnderlying(updateStatisticRef)(playerIdsToUpdate))
      UpdateStats(failed, success) <- updateStatisticRef.get
      _                            <- log.info(
                                        s"Updated successfully: ${success.size} players," +
                                          s"failed to update: ${failed.size} players." +
                                          s"Total duration: ${updateDuration.toSeconds} seconds."
                                      )
      _                            <- eventService.sendEvent(
                                        PlayersUpdateEvent(
                                          updateSuccess = success.sorted.map(PlayerId(_)),
                                          updateFailure = failed.sorted.map(PlayerId(_)),
                                          taskDurationSeconds = updateDuration.toSeconds.toInt,
                                          timestamp = now
                                        )
                                      )
    } yield ()

    override def updatePlayersValueInUserStates: F[Unit] = (for {
      _ <- EitherT.liftF[F, GameException, Unit](log.info("Starting players value update task for players in User Game States..."))
      now = timeProvider.getCurrentTimestamp
      allStates          <- EitherT(userGameStateService.getAllGameStates())
      updateStatisticRef <- EitherT.liftF(Ref.of[F, UpdateStats](UpdateStats()))
      playerIdsToUpdate = getAllPlayersFromUserStates(allStates).toList
      (updateDuration, _)          <- EitherT.liftF(Clock[F].timed(updatePlayersUnderlying(updateStatisticRef)(playerIdsToUpdate)))
      UpdateStats(failed, success) <- EitherT.liftF(updateStatisticRef.get)
      _                            <- EitherT.liftF(
                                        log.info(
                                          s"Updated successfully: ${success.size} players," +
                                            s"failed to update: ${failed.size} players." +
                                            s"Total duration: ${updateDuration.toSeconds} seconds."
                                        )
                                      )
      events                       <- allStates
                                        .toList
                                        .traverse { case (user, state) =>
                                          for {
                                            playerInfoEvent <-
                                              EitherT(state.portfolio.traverse(playerStockInfoToPlayerValueChangedEvent(now, user)).map(_.sequence))
                                            updatedPortfolio = playerInfoEvent.map { case (info, _) => info }
                                            events           = playerInfoEvent.mapFilter { case (_, maybeEvent) => maybeEvent }
                                            updatedState     = domain.UserGameState(
                                                                 user = user,
                                                                 portfolio = updatedPortfolio,
                                                                 money = state.money,
                                                                 updatedAt = now,
                                                                 wishlist = state.wishlist
                                                               )
                                            versionNumber    = state.updatedAt
                                            _ <- EitherT(
                                                   userGameStateService.updateGameStateForUser(user)(updatedState)(versionNumber)
                                                 ) //todo: we can ignore this update if nothing changed (only: updatedAt = now)
                                          } yield events
                                        }
                                        .map(_.flatten)
      _                            <- EitherT.liftF[F, GameException, Unit](log.info(s"Sending ${events.size} PlayerValueChanged events"))
      _                            <- EitherT.liftF[F, GameException, List[Unit]](events.map(eventService.sendEvent).sequence)
    } yield ()).rethrowT

    case class UpdateStats(failed: List[Int] = Nil, success: List[Int] = Nil)

    private def getAllPlayersFromUserStates(userStates: Map[User, UserGameState]): Set[PlayerId] =
      userStates.toList.map(_._2).map(_.portfolio).flatMap(_.map(_.playerId)).toSet

    private def updatePlayersUnderlying(ref: Ref[F, UpdateStats])(playersIdToUpdate: List[PlayerId]): F[Unit] = fs2
      .Stream
      .emits[F, PlayerId](playersIdToUpdate)
      .parEvalMapUnordered(maxConcurrent)(updatePlayerProfileInMemory(ref))
      .compile
      .drain

    private def getIsRetired(playerJson: Json): Boolean =
      playerJson.findAllByKey("isRetired").headOption.flatMap(_.asBoolean).getOrElse(false)

    private def getUpdatedAt(playerJson: Json): Instant =
      toInstantOrFarPastForUpdateAt(playerJson.findAllByKey("updatedAt").headOption.flatMap(_.asString))

    private def filteredPlayers(
      threshold: Instant
    )(
      players: Map[PlayerId, Json]
    ): F[List[PlayerId]] = for {
      allPlayers      <- players.toList.pure
      _               <- log.debug(s"Found ${allPlayers.size} players in memory")
      filteredPlayers <- allPlayers.filter { case (_, json) => !getIsRetired(json) && getUpdatedAt(json).isBefore(threshold) }.pure
      filteredIds     <- filteredPlayers.map { case (id, _) => id }.pure
      _               <- log.debug(s"Found ${filteredIds.size} players matching criteria for update")
    } yield filteredIds

    private def updatePlayerProfileInMemory(
      ref: Ref[F, UpdateStats]
    )(
      playerId: PlayerId
    ): F[Unit] = playerService.refreshPlayerProfileInMemory(playerId).flatMap {
      case Left(err) =>
        ref.update { case UpdateStats(failed, success) => UpdateStats(failed :+ playerId.value, success) } *>
          log.debug(s"$playerId NOT updated: $err")
      case Right(_)  =>
        ref.update { case UpdateStats(failed, success) => UpdateStats(failed, success :+ playerId.value) } *>
          log.debug(s"$playerId updated")
    }

    private def playerStockInfoToPlayerValueChangedEvent(
      now: Instant,
      user: User
    )(
      stockInfo: StockInfo
    ): F[Either[GameException, (StockInfo, Option[PlayerValueChanged])]] = (for {
      freshPlayerProfile <- EitherT(playerService.getPlayerProfileById(stockInfo.playerId))
      freshPlayerStats   <- EitherT(playerService.getPlayerStatsById(stockInfo.playerId))
      freshPlayerValue      = freshPlayerProfile.marketValue
      previousPlayerValue   = stockInfo.lastPlayerValue
      playerName            = freshPlayerProfile.name
      (newStockInfo, event) = previousPlayerValue equals freshPlayerValue match {
                                case true  => (stockInfo, None)
                                case false =>
                                  val newStockInfo = StockInfo(
                                    playerId = stockInfo.playerId,
                                    shares = stockInfo.shares,
                                    lastPlayerValue = freshPlayerValue,
                                    lastPlayerMinutesPlayed =
                                      freshPlayerStats.totalMinutesPlayed //todo: this will override lastPlayerMinutesPlayed and may cause dividend will not be added if override happened before update task
                                  )
                                  val event = Some(
                                    PlayerValueChanged(
                                      playerId = stockInfo.playerId,
                                      playerName = playerName,
                                      previousValue = previousPlayerValue,
                                      newValue = freshPlayerValue,
                                      user = user,
                                      timestamp = now
                                    )
                                  )
                                  (newStockInfo, event)
                              }
    } yield (newStockInfo, event)).value

  }

}
