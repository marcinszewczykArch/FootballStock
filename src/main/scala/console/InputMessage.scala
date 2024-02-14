package console

import cats.Applicative
import game.GameException
import GameException.IncorrectConsoleInputException
import GameException.IncorrectParsingException
import utils.Type.ErrorOr

sealed trait InputMessage

case class SearchPlayerByName(input: String)   extends InputMessage
case class GetPlayerProfileById(playerId: Int) extends InputMessage
case class GetPlayerValueById(playerId: Int)   extends InputMessage
case class GetPlayerValueHistoryById(playerId: Int)   extends InputMessage

case class CreateNewUser(user: String)                          extends InputMessage
case class GetUserState(user: String)                           extends InputMessage
case class GetAllUsersStates()                                  extends InputMessage
case class GetUserBalance(user: String)                         extends InputMessage
case class GetUserEvents(user: String)                          extends InputMessage
case class BuyShares(user: String, playerId: Int, shares: Int)  extends InputMessage
case class SellShares(user: String, playerId: Int, shares: Int) extends InputMessage

case class Error(message: String) extends InputMessage

object InputMessage {

  def parse[F[_]: Applicative](inputText: String): F[ErrorOr[InputMessage]] =
    Applicative[F].pure {
      splitWords(inputText) match {
        case ("/search", input, input2, input3) => Right(SearchPlayerByName(input + input2 + input3))
        case ("/player", playerId, _, _)        => playerId.toIntOption.map(GetPlayerProfileById).toRight(IncorrectParsingException(playerId))
        case ("/value", playerId, _, _)         => playerId.toIntOption.map(GetPlayerValueById).toRight(IncorrectParsingException(playerId))
        case ("/valueHistory", playerId, _, _)         => playerId.toIntOption.map(GetPlayerValueHistoryById).toRight(IncorrectParsingException(playerId))


        case ("/newUser", user, _, _) => Right(CreateNewUser(user))
        case ("/state", user, _, _)   => Right(GetUserState(user))
        case ("/allStates", _, _, _)  => Right(GetAllUsersStates())
        case ("/balance", user, _, _) => Right(GetUserBalance(user))
        case ("/event", user, _, _)  => Right(GetUserEvents(user))

        case ("/buy", user, playerId, shares)  =>
          (for {
            playerIdInt <- playerId.toIntOption
            sharesInt   <- shares.toIntOption
          } yield BuyShares(user, playerIdInt, sharesInt)).toRight(IncorrectParsingException(playerId + " / " + shares))
        case ("/sell", user, playerId, shares) =>
          (for {
            playerIdInt <- playerId.toIntOption
            sharesInt   <- shares.toIntOption
          } yield SellShares(user, playerIdInt, sharesInt)).toRight(IncorrectParsingException(playerId + " / " + shares))

        case _ => Left(IncorrectConsoleInputException(inputText))
      }
    }

  private def splitWords(text: String): (String, String, String, String) = {
    val (first, rest)   = splitTwo(text)
    val (second, rest2) = splitTwo(rest)
    val (third, fourth) = splitTwo(rest2)
    (first, second, third, fourth)
  }

  private def splitTwo(text: String): (String, String) = {
    val trimmedText: String = text.trim
    val firstSpace: Int     = trimmedText.indexOf(' ')
    if (firstSpace < 0)
      (trimmedText, "")
    else
      (trimmedText.substring(0, firstSpace), trimmedText.substring(firstSpace + 1).trim)
  }

}
