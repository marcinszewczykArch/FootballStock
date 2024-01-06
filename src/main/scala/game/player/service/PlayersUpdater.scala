package game.player.service

import cats.Applicative
import cats.data.EitherT
import cats.effect.Async
import cats.effect.Ref
import cats.effect.kernel.Clock
import cats.implicits.catsSyntaxApplicativeId
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import game.player.client.PlayerProfileClient
import game.player.memory.PlayerProfileClientMemory
import game.player.service.PlayersUpdater.UpdateCriteria
import game.player.service.domain.PlayerId
import io.circe.Json
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import utils.Parser.parseInstant
import utils.TimeProvider

import java.time.Instant
import scala.concurrent.duration.FiniteDuration

trait PlayersUpdater[F[_]] {
  def updatePlayersInMemory(playersUpdateCriteria: UpdateCriteria): F[Unit]
}

object PlayersUpdater {

  def impl[F[_]: Async: LoggerFactory](
    playerProfileClient: PlayerProfileClient[F],
    playerProfileClientMemory: PlayerProfileClientMemory[F]
  )(
    implicit timeProvider: TimeProvider[F]
  ) = new PlayersUpdater[F] {
    val maxConcurrent = 8
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayersUpdater[F]].getName)

    override def updatePlayersInMemory(criteria: UpdateCriteria): F[Unit] = for {
      _                            <- log.info("Starting players update task...")
      now                          <- Applicative[F].pure(timeProvider.getCurrentTimestamp)
      thresholdTimestamp           <- Applicative[F].pure(now.minusSeconds(criteria.notUpdatedFor.toSeconds))
      updateStatisticRef           <- Ref.of[F, UpdateStats](UpdateStats())
      (updateDuration, _)          <- Clock[F].timed(underlying(updateStatisticRef)(thresholdTimestamp))
      UpdateStats(failed, success) <- updateStatisticRef.get
      _                            <- log.info(
                                        s"Updated successfully ${success.size} players, failed to update ${failed.size}. " +
                                          s"Total duration: ${updateDuration.toSeconds} seconds."
                                      )
//      _                               <- sendEventWithLoadStatistic(loadStatistic) //todo: send event with statistics
    } yield ()

    case class UpdateStats(failed: List[Long] = Nil, success: List[Long] = Nil)

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
    ): F[List[PlayerId]] = players
      .toList
      .filter { case (_, json) => !getIsRetired(json) && getUpdatedAt(json).isBefore(threshold) }
      .map { case (id, _) => id }
      .pure

    private def updatePlayerProfileInMemory(
      ref: Ref[F, UpdateStats]
    )(
      playerId: PlayerId
    ): F[Unit] = (for {
      json <- EitherT(playerProfileClient.fetchRawPlayerProfileById(playerId))
      _    <- EitherT(playerProfileClientMemory.save(playerId)(json))
    } yield ()).value.map {
      case Left(err) =>
        ref.update { case UpdateStats(failed, success) => UpdateStats(failed :+ playerId.value, success) } *>
          log.error(s"$playerId NOT updated: $err")
      case Right(_)  =>
        ref.update { case UpdateStats(failed, success) => UpdateStats(failed, success :+ playerId.value) } *>
          log.info(s"$playerId updated")
    }

  }

  case class UpdateCriteria(notUpdatedFor: FiniteDuration)

}
