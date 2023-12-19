package errors

sealed abstract class GameException(message: String) extends Throwable(message)

object GameException {
  final case class IncorrectConsoleInputException(input: String) extends GameException(s"Incorrect input: $input.")

  final case class UserNotFoundException(userName: String) extends GameException(s"UserState for user $userName not found.")

  final case class NotEnoughMoneyException(available: BigDecimal, required: BigDecimal)
    extends GameException(s"Not enough money to buy. Required: $required, but available: $available.")

  final case class ValueParseException(value: Option[String], err: Throwable)
    extends GameException(s"Not able to parse player value: $value, because of exception: $err.")

  final case class TooManySharesException(newShares: Double)
    extends GameException(s"Too many shares selected to buy (new shares number = $newShares). Total must be below 1.0.")

  final case class PlayerMarketValueNotFoundException(playerId: Int, err: String)
    extends GameException(s"Market value for player with id [$playerId] not found. The reason is: $err")

  final case class PlayerSearchByNameException(playerName: String, err: String)
    extends GameException(s"Could not find player by name [$playerName]. The reason is: $err")
}