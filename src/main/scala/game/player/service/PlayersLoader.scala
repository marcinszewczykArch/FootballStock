package game.player.service

import cats.Applicative
import cats.MonadThrow
import cats.effect.Async
import cats.effect._
import cats.implicits.catsSyntaxApplicativeError
import cats.implicits.toFunctorOps
import cats.effect.Sync
import cats.implicits.catsSyntaxApplyOps
import game.player.client.PlayerProfileClient
import game.player.client.domain.FetchedPlayerProfile
import game.player.service.domain.PlayerId
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import sttp.client3.logging.Logger
import utils.Cache
import utils.RetryHandler.runRetry
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import cats.implicits.toTraverseOps

import scala.util.Try

trait PlayersLoader[F[_]] {
  def loadPlayersToCache(from: Int, to: Int): F[Unit]
}

object PlayersLoader {

  def impl[F[_]: Async: LoggerFactory](playerProfileClient: PlayerProfileClient[F]) = new PlayersLoader[F] {
    val maxConcurrent = 8
    implicit val log: SelfAwareStructuredLogger[F] =
      LoggerFactory.getLoggerFromName[F](classOf[PlayersLoader[F]].getName)

    override def loadPlayersToCache(from: Int, to: Int): F[Unit] = fs2
      .Stream(from to to: _*)
      .covary[F]
      .parEvalMapUnordered(maxConcurrent) { id =>
        log.info(s"Fetching PlayerId($id)") *>
          runRetry(3) {
            playerProfileClient.fetchPlayerProfileById(PlayerId(id)).attempt
          }
      }
      .compile
      .drain

  }

}
