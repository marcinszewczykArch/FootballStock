package console

import cats.Monad
import cats.Applicative
import cats.effect.std.Console
import cats.syntax.all._
import errors.GameException
import httpClient.TransfermarktClient

import scala.io.AnsiColor._

trait ConsolePrinter[F[_]] {
  def readMessage[F[_]: Console: Monad]: F[InputMessage]
  def gameLoop[F[_]: Console: Monad](transfermarktClient: TransfermarktClient[F])(message: InputMessage): F[Unit]
  def printStartMessage[F[_]: Applicative]: F[Unit]
}

object ConsolePrinter {

  def impl[F[_]: Console: Monad] = new ConsolePrinter[F] {

    def readMessage[F[_]: Console: Monad]: F[InputMessage] = for {
      stringInput <- readUserInputFromConsole[F]
      message     <- InputMessage.parse[F](stringInput)
    } yield message match {
      case Right(inputMessage: InputMessage) => inputMessage
      case Left(exception: GameException)    => Error(exception.message)
    }

    def gameLoop[F[_]: Console: Monad](transfermarktClient: TransfermarktClient[F])(message: InputMessage): F[Unit] =
      message match {
        case SearchPlayer(input) =>
          for {
            players <- transfermarktClient.searchPlayer(input)
            _          <- Applicative[F].pure(println(players))
          } yield ()
        case Error(msg)         => printErrorMessage[F](msg) *> printInstruction[F]
      }

    def printStartMessage[F[_]: Applicative]: F[Unit] =
      Applicative[F].pure(println(">>>>>> Football Stock game <<<<<<")) *> printInstruction[F]

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
