package console

import cats.effect.IO
import cats.{Applicative, Monad}
import cats.effect.std.Console
import cats.syntax.all._
import errors.GameException
import httpClient.TransfermarktClient
import httpClient.domain.PlayerSearch
import services.PlayerService
import services.domain.PlayerSimple

import scala.io.AnsiColor._

trait ConsolePrinter[F[_]] {
  def readMessage[F[_]: Console: Monad]: F[InputMessage]
  def gameLoop[F[_]: Console: Monad](playerService: PlayerService[F])(message: InputMessage): F[Unit]
  def printStartMessage[F[_]: Applicative]: F[Unit]
}

object ConsolePrinter {

  def impl[F[_]: Console: Monad] = new ConsolePrinter[F] {

    def readMessage[F[_]: Console: Monad]: F[InputMessage] = for {
      stringInput <- readUserInputFromConsole[F]
      message     <- InputMessage.parse[F](stringInput)
    } yield message match {
      case Right(inputMessage: InputMessage) => inputMessage
      case Left(exception: GameException)    => Error(exception.getMessage)
    }

    def gameLoop[F[_]: Console: Monad](playerService: PlayerService[F])(message: InputMessage): F[Unit] =
      message match {
        case SearchPlayer(input) =>
          for {
            players <- playerService.searchByName(input)
            _       <- printPlayerSearchResult[F](players)
          } yield ()
        case Error(msg)          => printErrorMessage[F](msg) *> printInstruction[F]
      }

    def printStartMessage[F[_]: Applicative]: F[Unit] =
      Applicative[F].pure(println(">>>>>> Football Stock game <<<<<<")) *> printInstruction[F]

  }

  val dformatter = java.text.NumberFormat.getIntegerInstance

  private def printPlayerSearchResult[F[_]: Applicative](players: List[PlayerSimple]): F[Unit] = Applicative[F].pure {
    println("id | name | position | club | age | nationality | marketValue")
    players.foreach { case PlayerSimple(id, name, position, club, age, nationality, marketValue) =>
      println(
        id + " | " +
          name + " | " +
          position + " | " +
          club + " | " +
          age + " | " +
          nationality + " | " +
          dformatter.format(marketValue.toInt) + " €"
      )
    }
  }

  private def readUserInputFromConsole[F[_]: Console: Monad]: F[String] = Console[F].readLine

  private def printInstruction[F[_]: Applicative] =
    Applicative[F].pure {
      println(
        s"""
        |${GREEN}
        |Type:
        |"/search {player name}" - to search
        |${RESET}
        |""".stripMargin
      )
    }

  private def printErrorMessage[F[_]: Applicative](message: String) = Applicative[F].pure(println(message))

}
