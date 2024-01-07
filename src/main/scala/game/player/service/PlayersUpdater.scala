package game.player.service

import cats.Applicative
import cats.data.EitherT
import cats.effect.{Async, Ref}
import cats.effect.kernel.Clock
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxApplyOps, toFlatMapOps, toFunctorOps}
import config.AppConfig.PlayersUpdateCriteriaConfig
import game.events.PlayersUpdateEvent
import game.events.memory.EventMemory
import game.player.client.PlayerProfileClient
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.domain.PlayerId
import io.circe.Json
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.Parser.parseInstant
import utils.TimeProvider

import java.time.Instant

trait PlayersUpdater[F[_]] {
  def updatePlayersInMemory: F[Unit]
}

object PlayersUpdater {

  def impl[F[_]: Async: LoggerFactory](
    playerProfileClient: PlayerProfileClient[F],
    playerProfileClientMemory: PlayerProfileClientMemory[F],
    eventMemory: EventMemory[F],
    playersUpdateCriteria: PlayersUpdateCriteriaConfig
  )(
    implicit timeProvider: TimeProvider[F]
  ) = new PlayersUpdater[F] {
    val maxConcurrent = 8
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayersUpdater[F]].getName)

    override def updatePlayersInMemory: F[Unit] = for {
      _                            <- log.info("Starting players update task...")
      now                          <- Applicative[F].pure(timeProvider.getCurrentTimestamp)
      thresholdTimestamp           <- Applicative[F].pure(now.minusSeconds(playersUpdateCriteria.notUpdatedFor.toSeconds))
      updateStatisticRef           <- Ref.of[F, UpdateStats](UpdateStats())
      (updateDuration, _)          <- Clock[F].timed(underlying(updateStatisticRef)(thresholdTimestamp))
      UpdateStats(failed, success) <- updateStatisticRef.get
      _                            <- log.info(
                                        s"Updated successfully: ${success.size} players," +
                                          s"failed to update: ${failed.size} players." +
                                          s"Total duration: ${updateDuration.toSeconds} seconds."
                                      )
      _                            <- eventMemory.sendEvent(
                                        PlayersUpdateEvent(
                                          updateSuccess = success.map(PlayerId),
                                          updateFailure = failed.map(PlayerId),
                                          taskDurationSeconds = updateDuration.toSeconds.toInt,
                                          timestamp = now
                                        )
                                      )
    } yield ()

    case class UpdateStats(failed: List[Int] = Nil, success: List[Int] = Nil)

    private def underlying(ref: Ref[F, UpdateStats])(threshold: Instant): F[Unit] = fs2
      .Stream
      .evals(playerProfileClientMemory.getAll().flatMap(filteredPlayers(threshold)))
      .parEvalMapUnordered(maxConcurrent)(updatePlayerProfileInMemory(ref))
      .compile
      .drain

    def getIsRetired(playerJson: Json): Boolean =
      playerJson.findAllByKey("isRetired").headOption.flatMap(_.asBoolean).getOrElse(false)

    def getUpdatedAt(playerJson: Json): Instant =
      parseInstant(playerJson.findAllByKey("updatedAt").headOption.flatMap(_.asString))

    private def filteredPlayers(
      threshold: Instant
    )(
      players: Map[PlayerId, Json]
    ): F[List[PlayerId]] = for {
      allPlayers      <- players.toList.pure
      _               <- log.info(s"Found ${allPlayers.size} players in memory")
      filteredPlayers <- allPlayers.filter { case (_, json) => !getIsRetired(json) && getUpdatedAt(json).isBefore(threshold) }.pure
      filteredIds     <- filteredPlayers.map { case (id, _) => id }.pure
      _               <- log.info(s"Found ${filteredIds.size} players matching criteria for update")
    } yield filteredIds

    private def updatePlayerProfileInMemory(
      ref: Ref[F, UpdateStats]
    )(
      playerId: PlayerId
    ): F[Unit] = (for {
      json <- EitherT(playerProfileClient.fetchRawPlayerProfileById(playerId))
      _    <- EitherT(playerProfileClientMemory.save(playerId)(json))
    } yield ()).value.flatMap {
      case Left(err) =>
        ref.update { case UpdateStats(failed, success) => UpdateStats(failed :+ playerId.value, success) } *>
          log.error(s"$playerId NOT updated: $err")
      case Right(_)  =>
        ref.update { case UpdateStats(failed, success) => UpdateStats(failed, success :+ playerId.value) } *>
          log.info(s"$playerId updated")
    }

  }

}
