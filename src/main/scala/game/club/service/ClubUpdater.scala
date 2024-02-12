package game.club.service

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
import game.GameException
import game.event.Event
import game.event.Event.PlayerValueChanged
import game.event.Event.PlayersUpdateEvent
import game.event.service.EventService
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

trait ClubUpdater[F[_]] {
  def updateAllClubsInMemory: F[Unit]
}

object ClubUpdater {

  def impl[F[_]: Async: LoggerFactory]() = new ClubUpdater[F] {
    val maxConcurrent                              = 8
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[ClubUpdater[F]].getName)

    override def updateAllClubsInMemory: F[Unit] = ???

  }

}
