package console

import cats.Applicative
import cats.Monad
import cats.effect.std.Console
import cats.syntax.all._
import game.errors.GameException
import game.logic.GameEngine
import game.memory.StateMemory
import game.player.service.PlayerService
import game.player.service.domain.{PlayerId, PlayerSimple}

import scala.io.AnsiColor._
import scala.io.AnsiColor._

trait ConsolePrinter[F[_]] {
  def readMessage[F[_]: Console: Monad]: F[InputMessage]
  def gameLoop[F[_]: Console: Monad](playerService: PlayerService[F], gameLogic: GameEngine[F])(message: InputMessage): F[Unit]
  def printStartMessage[F[_]: Applicative]: F[Unit]
}

object ConsolePrinter {

  val dformatter = java.text.NumberFormat.getIntegerInstance

  def impl[F[_]: Console: Monad] = new ConsolePrinter[F] {

    def readMessage[F[_]: Console: Monad]: F[InputMessage] = for {
      stringInput <- readUserInputFromConsole[F]
      message     <- InputMessage.parse[F](stringInput)
    } yield message match {
      case Right(inputMessage: InputMessage) => inputMessage
      case Left(exception: GameException)    => Error(exception.getMessage)
    }

    def gameLoop[F[_]: Console: Monad](playerService: PlayerService[F], gameLogic: GameEngine[F])(message: InputMessage): F[Unit] =
      message match {
        case SearchPlayerByName(input)          =>
          for {
            players <- playerService.searchByName(input)
            _       <- printPlayerSearchResult[F](players)
          } yield ()
        case GetPlayerProfileById(id)           =>
          for {
            playerProfile <- playerService.getPlayerProfileById(PlayerId(id))
            _             <- prettyPrintOr[F](playerProfile)("Player profile not found")
          } yield ()
        case GetPlayerValueById(id)             =>
          for {
            playerValue <- playerService.getMarketValueByPlayerId(PlayerId(id))
            _           <- prettyPrintOr[F](playerValue)("Player value not found")
          } yield ()
        case GetUserState(user)                 =>
          for {
            userState <- gameLogic.getUserState(user)
            _         <- prettyPrintOr[F](userState)("User game state not found")
          } yield ()
        case GetUserBalance(user)                 =>
          for {
            userState <- gameLogic.getUserBalance(user)
            _         <- prettyPrintOr[F](userState)("User game balance not found")
          } yield ()
        case GetUserEvents(user)                 =>
          for {
            userState <- gameLogic.getUserEvents(user)
            _         <- prettyPrintOr[F](userState)("User events not found")
          } yield ()
        case BuyShares(user, playerId, shares)  =>
          for {
            confirmation <- gameLogic.buyPlayer(user)(PlayerId(playerId), shares)
            _            <- prettyPrintOr[F](confirmation)("Transaction error")
          } yield ()
        case SellShares(user, playerId, shares) =>
          for {
            confirmation <- gameLogic.sellPlayer(user)(PlayerId(playerId), shares)
            _            <- prettyPrintOr[F](confirmation)("Transaction error")
          } yield ()

        case Error(msg) => printErrorMessage[F](msg) *> printInstruction[F]
      }

    def printStartMessage[F[_]: Applicative]: F[Unit] =
      Applicative[F].pure(println(">>>>>> Football Stock game <<<<<<")) *> printInstruction[F]

  }

  private def printPlayerSearchResult[F[_]: Applicative](maybePlayers: Either[GameException, List[PlayerSimple]]): F[Unit] =
    maybePlayers match {
      case Left(err)      => Applicative[F].pure(println(s"Search failed. Reason: $err"))
      case Right(players) =>
        Applicative[F].pure {
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
    }

  private def prettyPrintOr[F[_]: Applicative](maybeObject: Either[GameException, Object])(errorMessage: String = ""): F[Unit] =
    maybeObject match {
      case Left(err)  => Applicative[F].pure(println(s"$errorMessage. Reason: $err"))
      case Right(obj) =>
        Applicative[F].pure {
          import utils.Parser.CaseClassToString
          obj.toStringWithFields.foreach { case (param, value) =>
            println(param + ": " + value)
          }
        }
    }

  private def readUserInputFromConsole[F[_]: Console: Monad]: F[String] = Console[F].readLine

  private def printInstruction[F[_]: Applicative] =
    Applicative[F].pure {
      println(
        s"""
        |${GREEN}
        |Type:
        |"/search {player name}" - to search player
        |"/player {player id}" - to get player profile
        |"/value {player id}" - to get player value
        |
        |"/state {user name}" - to display user state
        |"/buy {user name} {player id} {shares number 1-100}" - to buy shares
        |"/sell {user name} {player id} {shares number 1-100}" - to sell shares
        |${RESET}
        |""".stripMargin
      )
    }

  private def printErrorMessage[F[_]: Applicative](message: String) = Applicative[F].pure(println(message))

}
