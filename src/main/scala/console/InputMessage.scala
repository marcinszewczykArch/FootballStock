package console

import cats.Applicative
import errors.GameException
import errors.GameException.IncorrectConsoleInputException
import errors.GameException.IncorrectPlayerIdException
import multiplayer.domain.UserGameState

import scala.util.Failure
import scala.util.Failure
import scala.util.Success
import scala.util.Try

sealed trait InputMessage

case class SearchPlayerByName(input: String) extends InputMessage
case class GetPlayerProfileById(playerId: Int) extends InputMessage
case class GetPlayerValueById(playerId: Int) extends InputMessage
case class Error(message: String) extends InputMessage

object InputMessage {

  def parse[F[_]: Applicative](inputText: String): F[Either[GameException, InputMessage]] =
    Applicative[F].pure {
      splitWords(inputText) match {
        //todo case (nickGracza, komenda, parametr) np. ("marcin_132", "search", "andrzej niedzielan")
        case ("/search", input, input2) => Right(SearchPlayerByName(input + input2))
        case ("/player", input, _)      => input.toIntOption.map(GetPlayerProfileById).toRight(IncorrectPlayerIdException(input))
        case ("/value", input, _)       => input.toIntOption.map(GetPlayerValueById).toRight(IncorrectPlayerIdException(input))
        case _                          => Left(IncorrectConsoleInputException(inputText))
      }
    }

  private def splitWords(text: String): (String, String, String) = {
    val (first, rest) = splitTwo(text)
    val (second, third) = splitTwo(rest)
    (first, second, third)
  }

  private def splitTwo(text: String): (String, String) = {
    val trimmedText: String = text.trim
    val firstSpace: Int = trimmedText.indexOf(' ')
    if (firstSpace < 0)
      (trimmedText, "")
    else
      (trimmedText.substring(0, firstSpace), trimmedText.substring(firstSpace + 1).trim)
  }

}
