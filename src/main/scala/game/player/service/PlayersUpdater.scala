package game.player.service

import cats.Applicative
import cats.data.EitherT
import cats.data.OptionT
import cats.effect.Async
import cats.effect.Ref
import cats.effect.kernel.Clock
import cats.implicits.catsSyntaxApplicativeId
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorFilterOps
import cats.implicits.toFunctorOps
import cats.implicits.toTraverseOps
import config.AppConfig.PlayersUpdateCriteriaConfig
import game.errors.GameException
import game.events.Event
import game.events.Event.PlayerValueChanged
import game.events.Event.PlayersUpdateEvent
import game.events.service.EventService
import game.player.client.PlayerProfileClient
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.domain.PlayerId
import game.state.domain.Shares
import game.state.domain.StockInfo
import game.state.domain.User
import game.state.domain.UserGameState
import game.state.service.UserGameStateService
import io.circe.Json
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import utils.Parser.toInstantOrFarPastForUpdateAt
import utils.TimeProvider

import java.time.Instant

trait PlayersUpdater[F[_]] {
  def updateAllPlayersInMemory: F[Unit]
  def updatePlayersValueInUserStates: F[Unit]
}

object PlayersUpdater {

  def impl[F[_]: Async: LoggerFactory](
    playerProfileClient: PlayerProfileClient[F],
    playerProfileClientMemory: PlayerProfileClientMemory[F], //to fetch data directly from DB
    playerProfileClientMemoryCached: PlayerProfileClientMemory[F], //to update with cache update
    playerService: PlayerService[F],
    eventService: EventService[F],
    userGameStateService: UserGameStateService[F],
    playersUpdateCriteria: PlayersUpdateCriteriaConfig
  )(
    implicit timeProvider: TimeProvider[F]
  ) = new PlayersUpdater[F] {
    val maxConcurrent                              = 8
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayersUpdater[F]].getName)

    override def updateAllPlayersInMemory: F[Unit] = for { //todo: test for this
      _                            <- log.info("Starting players profile update task for all players in memory...")
      now                          <- Applicative[F].pure(timeProvider.getCurrentTimestamp)
      thresholdTimestamp           <- Applicative[F].pure(now.minusSeconds(playersUpdateCriteria.notUpdatedFor.toSeconds))
      updateStatisticRef           <- Ref.of[F, UpdateStats](UpdateStats())
      playerIdsToUpdate            <- playerProfileClientMemory.getAll().flatMap(filteredPlayers(thresholdTimestamp))
      (updateDuration, _)          <- Clock[F].timed(updatePlayersUnderlying(updateStatisticRef)(playerIdsToUpdate))
      UpdateStats(failed, success) <- updateStatisticRef.get
      _                            <- log.info(
                                        s"Updated successfully: ${success.size} players," +
                                          s"failed to update: ${failed.size} players." +
                                          s"Total duration: ${updateDuration.toSeconds} seconds."
                                      )
      _                            <- eventService.sendEvent(
                                        PlayersUpdateEvent(
                                          updateSuccess = success.map(PlayerId(_)),
                                          updateFailure = failed.map(PlayerId(_)),
                                          taskDurationSeconds = updateDuration.toSeconds.toInt,
                                          timestamp = now
                                        )
                                      )
    } yield ()

    override def updatePlayersValueInUserStates: F[Unit] = (for { //todo: test for this
      _                  <- EitherT.liftF[F, GameException, Unit](log.info("Starting players value update task for players in User Game States..."))
      now                = timeProvider.getCurrentTimestamp
      allStates          <- EitherT(userGameStateService.getAllGameStates())
      updateStatisticRef <- EitherT.liftF(Ref.of[F, UpdateStats](UpdateStats()))
      playerIdsToUpdate = getAllPlayersFromUserStates(allStates).toList
      (updateDuration, _)          <- EitherT.liftF(Clock[F].timed(updatePlayersUnderlying(updateStatisticRef)(playerIdsToUpdate)))
      UpdateStats(failed, success) <- EitherT.liftF(updateStatisticRef.get)
      _                            <- EitherT.liftF(log.info(
                                        s"Updated successfully: ${success.size} players," +
                                          s"failed to update: ${failed.size} players." +
                                          s"Total duration: ${updateDuration.toSeconds} seconds."
                                      ))
      events      <- allStates //todo: improve me: to separate method
                                        .toList
                                        .traverse { case (user, state) =>
                                          for {
                                            playerInfoEvent <-
                                              EitherT(
                                                state
                                                  .portfolio
                                                  .toList
                                                  .traverse { case (playerId, info) =>
                                                    (for {
                                                      freshPlayerProfile <- EitherT(playerService.getPlayerProfileById(playerId))
                                                      freshPlayerValue      = freshPlayerProfile.marketValue
                                                      previousPlayerValue   = info.lastPlayerValue
                                                      playerName            = freshPlayerProfile.name
                                                      (newStockInfo, event) = previousPlayerValue equals freshPlayerValue match {
                                                                                case true  => (info, None)
                                                                                case false =>
                                                                                  val newStockInfo = StockInfo(
                                                                                    shares = info.shares,
                                                                                    lastPlayerValue = freshPlayerValue
                                                                                  )
                                                                                  val event        = Some(
                                                                                    PlayerValueChanged(
                                                                                      playerId = playerId,
                                                                                      playerName = playerName,
                                                                                      previousValue = previousPlayerValue,
                                                                                      newValue = freshPlayerValue,
                                                                                      user = user,
                                                                                      timestamp = now
                                                                                    )
                                                                                  )
                                                                                  (newStockInfo, event)
                                                                              }
                                                    } yield (playerId, newStockInfo, event)).value
                                                  }
                                                  .map(_.sequence)
                                              )
                                            updatedPortfolio = playerInfoEvent.map { case (id, info, _) => (id, info) }.toMap
                                            events               = playerInfoEvent.mapFilter(_._3)
                                            updatedState         = UserGameState(
                                                                     portfolio = updatedPortfolio,
                                                                     money = state.money,
                                                                     updatedAt = now
                                                                   )
                                            versionNumber = state.updatedAt
                                            _ <- EitherT(userGameStateService.updateGameStateFroUser(user)(updatedState)(versionNumber))
                                          } yield events
                                        }.map(_.flatten)
      _                            <- EitherT.liftF[F, GameException, Unit](log.info(s"Sending ${events.size} PlayerValueChanged events"))
      _                            <- EitherT.liftF[F, GameException, List[Unit]](events.map(eventService.sendEvent).sequence)
    } yield ()).rethrowT

    case class UpdateStats(failed: List[Int] = Nil, success: List[Int] = Nil)

    private def getAllPlayersFromUserStates(userStates: Map[User, UserGameState]): Set[PlayerId] =
      userStates.toList.map(_._2).map(_.portfolio).flatMap(_.toList.map(_._1)).toSet

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
    ): F[Unit] = (for {
      json <- EitherT(playerProfileClient.fetchRawPlayerProfileById(playerId))
      _    <- EitherT(playerProfileClientMemoryCached.save(playerId)(json))
    } yield ()).value.flatMap {
      case Left(err) =>
        ref.update { case UpdateStats(failed, success) => UpdateStats(failed :+ playerId.value, success) } *>
          log.debug(s"$playerId NOT updated: $err")
      case Right(_)  =>
        ref.update { case UpdateStats(failed, success) => UpdateStats(failed, success :+ playerId.value) } *>
          log.debug(s"$playerId updated")
    }

  }

}
