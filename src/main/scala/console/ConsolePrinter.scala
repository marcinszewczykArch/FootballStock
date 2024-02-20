package console

import cats.{Applicative, Monad}
import cats.effect.std.Console
import cats.syntax.all._
import game.modules.player.service.domain.{PlayerId, PlayerSimple}
import game.modules.state.domain.User
import game.{GameEngine, GameException}
import utils.Type.ErrorOr

import scala.io.AnsiColor._

trait ConsolePrinter[F[_]] {
  def readMessage[F[_]: Console: Monad]: F[InputMessage]
  def gameLoop[F[_]: Console: Monad](gameLogic: GameEngine[F])(message: InputMessage): F[Unit]
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

    def gameLoop[F[_]: Console: Monad](gameLogic: GameEngine[F])(message: InputMessage): F[Unit] =
      message match {
        case SearchPlayerByName(input)     =>
          for {
            players <- gameLogic.searchPlayerByName(input)
            _       <- printPlayerSearchResult[F](players)
          } yield ()
        case GetPlayerProfileById(id)      =>
          for {
            playerProfile <- gameLogic.getPlayerProfileById(PlayerId(id))
            _             <- prettyPrintOr[F](playerProfile)("Player profile not found")
          } yield ()
        case GetPlayerValueById(id)        =>
          for {
            playerValue <- gameLogic.getMarketValueByPlayerId(PlayerId(id))
            _           <- prettyPrintOr[F](playerValue)("Player value not found")
          } yield ()
        case GetPlayerValueHistoryById(id) =>
          for {
            playerValue <- gameLogic.getMarketValueHistoryByPlayerId(PlayerId(id))
            _           <- prettyPrintOr[F](playerValue)("Player value history not found")
          } yield ()

        case CreateNewUser(userName)                =>
          for {
            initializeGameEvent <- gameLogic.createUser(User(userName))
            _                   <- prettyPrintOr[F](initializeGameEvent)(s"${User(userName)} could not be created")
          } yield ()
        case GetUserState(userName)                 =>
          for {
            userState <- gameLogic.getUserState(User(userName))
            _         <- prettyPrintOr[F](userState)("User game state not found")
          } yield ()
        case GetAllUsersStates()                    =>
          for {
            allUsersStates <- gameLogic.getAllUsersStates()
            _              <- prettyPrintOr[F](allUsersStates)("Could not get all users states")
          } yield ()
        case GetUserBalance(userName)               =>
          for {
            userState <- gameLogic.getUserBalance(User(userName))
            _         <- prettyPrintOr[F](userState)("User game balance not found")
          } yield ()
        case GetUserEvents(userName)                =>
          for {
            userEvents <- gameLogic.getUserEvents(User(userName))
            _ =
              userEvents
                .sequence
                .foreach(either =>
                  either.map(event => Applicative[F].pure(println(event.getEventName)) *> prettyPrintOr[F](either)("User events not found"))
                )
          } yield ()
        case BuyShares(userName, playerId, shares)  =>
          for {
            confirmation <- gameLogic.buyPlayer(User(userName))(PlayerId(playerId), shares)
            _            <- prettyPrintOr[F](confirmation)("Transaction error")
          } yield ()
        case SellShares(userName, playerId, shares) =>
          for {
            confirmation <- gameLogic.sellPlayer(User(userName))(PlayerId(playerId), shares)
            _            <- prettyPrintOr[F](confirmation)("Transaction error")
          } yield ()

        case Error(msg) => printErrorMessage[F](msg) *> printInstruction[F]
      }

    def printStartMessage[F[_]: Applicative]: F[Unit] =
      Applicative[F].pure(println(">>>>>> Football Stock game <<<<<<")) *> printInstruction[F]

  }

  private def printPlayerSearchResult[F[_]: Applicative](maybePlayers: ErrorOr[List[PlayerSimple]]): F[Unit] =
    maybePlayers match {
      case Left(err)      => Applicative[F].pure(println(s"Search failed. Reason: $err"))
      case Right(players) =>
        Applicative[F].pure {
          println("id | name | position | club | age | nationality | marketValue")
          players.foreach { case PlayerSimple(id, name, position, club, clubId, age, nationality, marketValue) =>
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

  private def prettyPrintOr[F[_]: Applicative](maybeObject: ErrorOr[Object])(errorMessage: String = ""): F[Unit] =
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
        |"/valueHistory {player id}" - to get player value history
        |
        |"/newUser {user name}" - to create new user
        |"/state {user name}" - to display user state
        |"/allStates" - to display all users states
        |"/events {user name}" - to display user events
        |"/buy {user name} {player id} {shares number 1-100}" - to buy shares
        |"/sell {user name} {player id} {shares number 1-100}" - to sell shares
        |${RESET}
        |""".stripMargin
      )
    }

  private def printErrorMessage[F[_]: Applicative](message: String) = Applicative[F].pure(println(message))

}
