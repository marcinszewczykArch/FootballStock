package console

import cats.{Applicative, Monad}
import cats.effect.std.Console
import cats.syntax.all._
import errors.GameException
import multiplayer.memory.StateMemory
import services.PlayerService
import services.domain.{MarketValue, PlayerProfile, PlayerSimple}

import scala.io.AnsiColor._

trait ConsolePrinter[F[_]] {
  def readMessage[F[_]: Console: Monad]: F[InputMessage]
  def gameLoop[F[_]: Console: Monad](playerService: PlayerService[F], stateMemory: StateMemory[F])(message: InputMessage): F[Unit]
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

    def gameLoop[F[_]: Console: Monad](playerService: PlayerService[F], stateMemory: StateMemory[F])(message: InputMessage): F[Unit] =
      message match {
        case SearchPlayerByName(input)   =>
          for {
            players <- playerService.searchByName(input)
            _       <- printPlayerSearchResult[F](players)
          } yield ()
        case GetPlayerProfileById(input) =>
          for {
            playerProfile <- playerService.getPlayerProfileById(input)
            _             <- printPlayerProfile[F](playerProfile)
          } yield ()
        case GetPlayerValueById(input)   =>
          for {
            playerValue <- playerService.getMarketValueByPlayerId(input)
            _           <- printPlayerValue[F](playerValue)
          } yield ()

        case GetUserState(user)                 =>
          for {
            userState <- stateMemory.getUserState(user)
            _         <- Applicative[F].pure(println(userState))
          } yield ()
        case BuyShares(user, playerId, shares)  =>
          for {
            confirmation <- stateMemory.buyPlayer(user)(playerId, shares)
            _            <- Applicative[F].pure(println(confirmation))
          } yield ()
        case SellShares(user, playerId, shares) =>
          for {
            confirmation <- stateMemory.sellPlayer(user)(playerId, shares)
            _            <- Applicative[F].pure(println(confirmation))
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

  private def printPlayerProfile[F[_]: Applicative](maybePlayerProfile: Either[GameException, PlayerProfile]): F[Unit] =
    maybePlayerProfile match {
      case Left(err)            => Applicative[F].pure(println(s"Player profile not found. Reason: $err"))
      case Right(playerProfile) =>
        Applicative[F].pure {
          import utils.Parser.CaseClassToString
          playerProfile.toStringWithFields.foreach { case (param, value) =>
            println(param + ": " + value)
          }
        }
    }

  private def printPlayerValue[F[_]: Applicative](maybePlayerValue: Either[GameException, MarketValue]): F[Unit] =
    maybePlayerValue match {
      case Left(err)          => Applicative[F].pure(println(s"Player value not found. Reason: $err"))
      case Right(playerValue) =>
        Applicative[F].pure {
          import utils.Parser.CaseClassToString
          playerValue.toStringWithFields.foreach { case (param, value) =>
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
        |${RESET}
        |""".stripMargin
      )
    }

  private def printErrorMessage[F[_]: Applicative](message: String) = Applicative[F].pure(println(message))

}
