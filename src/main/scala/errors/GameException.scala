package errors

sealed abstract class GameException(val message: String) extends Throwable(message)

final case class IncorrectConsoleInputException(input: String) extends GameException(s"Incorrect input: $input")
