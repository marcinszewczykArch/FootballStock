package game.player.service

import cats.Applicative
import cats.effect.Async
import cats.effect.Ref
import cats.effect.kernel.Clock
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import game.player.client.PlayerProfileClient
import game.player.memory.PlayerProfileClientMemory
import game.player.service.domain.PlayerId
import io.circe.Json
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import utils.Parser.parseInstant
import utils.TimeProvider

import java.time.Instant
import java.time.temporal.ChronoUnit

trait PlayersLoader[F[_]] {
  def loadPlayersToMemory(from: Int, to: Int): F[Unit]
}

object PlayersLoader {

  def impl[F[_]: Async: LoggerFactory](
    playerProfileClient: PlayerProfileClient[F],
    playerProfileClientMemory: PlayerProfileClientMemory[F]
                                      )(
    implicit timeProvider: TimeProvider[F]
  ) = new PlayersLoader[F] {
    val maxConcurrent = 8
    implicit val log: SelfAwareStructuredLogger[F] =
      LoggerFactory.getLoggerFromName[F](classOf[PlayersLoader[F]].getName)

    override def loadPlayersToMemory(from: Int, to: Int): F[Unit] = for {
      now               <- Applicative[F].pure(timeProvider.getCurrentTimestamp)
      resultRef         <- Ref.of[F, (Int, Int)](0, 0) //todo: object with two list of playerIds
      (duration, _)     <- Clock[F].timed(underlying(from, to)(resultRef, now))
      (failed, success) <- resultRef.get
      _                 <- log.info(
                             s"Loaded successfully $success players, failed to load $failed. " +
                               s"Total duration: ${duration.toSeconds} seconds."
                           )
    } yield () //todo: send event with conclusion which players have been saved to memory, which failed what time, duration etc.

    private def underlying(from: Int, to: Int)(ref: Ref[F, (Int, Int)], now: Instant) = fs2
      .Stream(from to to: _*)
      .covary[F]
      .map(PlayerId)
      .parEvalMapUnordered(maxConcurrent) { playerId =>
        log.info(s"$playerId checking presence in memory...") *>
          playerProfileClientMemory
            .getPlayerJson(playerId)
            .flatMap {
              case Right(json) =>
                log.info(s"$playerId found in memory. Checking update criteria...") *> {
                  isMeetingUpdateCriteria(now)(json) match {
                    case true  =>
                      log.info(s"$playerId is meeting update criteria. Proceed update...") *>
                        fetchFromHttpClient(ref)(playerId)
                    case false =>
                      log.info(s"$playerId not meeting update criteria. Ignore update.")
                  }
                }

              case Left(_) =>
                log.info(s"$playerId NOT found in memory. Fetching from http client...") *>
                  fetchFromHttpClient(ref)(playerId)
            }
      }
      .compile
      .drain

    private def fetchFromHttpClient(
      ref: Ref[F, (Int, Int)]
    )(
      playerId: PlayerId
    ): F[Unit] = playerProfileClient.fetchRawPlayerProfileById(playerId).flatMap {
      case Left(err)   =>
        ref.update { case (l, r) => (l + 1, r) } *>
          log.error(s"$playerId NOT found in http client call: $err")
      case Right(json) =>
        playerProfileClientMemory
          .savePlayerJson(playerId)(json)
          .flatMap {
            case Left(err) =>
              ref.update { case (l, r) => (l + 1, r) } *>
                log.error(s"$playerId NOT saved to memory: $err")
            case Right(_)  =>
              ref.update { case (l, r) => (l, r + 1) } *>
                log.info(s"$playerId saved to memory")
          }
    }

    private def isMeetingUpdateCriteria(now: Instant)(playerJsonFromMemory: Json): Boolean = {
      val isActivePlayer: Boolean = playerJsonFromMemory.findAllByKey("isRetired").map(_.toString()).contains("false")
      val lastUpdate: Instant = parseInstant(playerJsonFromMemory.findAllByKey("updatedAt").headOption.map(_.toString()))
      val isOlderThan12Hours = lastUpdate.isBefore(now.minus(12, ChronoUnit.HOURS))

      isActivePlayer && isOlderThan12Hours
    }

  }

}
