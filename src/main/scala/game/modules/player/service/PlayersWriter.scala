package game.modules.player.service

import cats.Applicative
import cats.effect.Async
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import game.modules.player.client.PlayerProfileClient
import game.modules.player.service.domain.PlayerId
import io.circe.Json
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger

trait PlayersWriter[F[_]] {
  def writeToFile(path: String, playerIds: List[Int]): F[Unit]
}

object PlayersWriter {

  def impl[F[_]: Async: LoggerFactory](
    playerProfileClient: PlayerProfileClient[F]
  ) = new PlayersWriter[F] {
    val maxConcurrent                              = 8
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[PlayersWriter[F]].getName)

    import java.io.File
    import java.io.PrintWriter

    override def writeToFile(path: String, playerIds: List[Int]): F[Unit] = for {
      _ <- log.info("Starting writing players to file...")
      _ <- underlying(path, playerIds)
      _ <- log.info("Writing players to file done!")
    } yield ()

    private def underlying(path: String, playerIds: List[Int]): F[Unit] = fs2
      .Stream(playerIds: _*)
      .covary[F]
      .map(PlayerId(_))
      .parEvalMapUnordered(maxConcurrent) { id =>
        val player = playerProfileClient.fetchRawPlayerProfileById(id).map(_.map((id, _)))
        player.flatMap {
          case Left(err)               => log.info(s"player with id $id NOT saved: $err")
          case Right((playerId, json)) =>
            createFile(path, playerId, json) *>
              log.info(s"player with id $id.json saved")
        }
      }
      .compile
      .drain

    private def createFile[F[_]: Async: LoggerFactory](
      path: String,
      playerId: PlayerId,
      json: Json
    ): F[Unit] = Applicative[F].pure {
      val file = new File(s"$path/${playerId.value}.json")
      val pw   = new PrintWriter(file)
      pw.write(json.toString())
      pw.close()
    }

  }

}
