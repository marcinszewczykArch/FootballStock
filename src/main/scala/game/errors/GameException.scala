package game.errors

import game.gameState.User
import game.player.service.domain.PlayerId
import io.circe.DecodingFailure

sealed abstract class GameException(message: String) extends Throwable(message)

object GameException {
  final case class IncorrectConsoleInputException(input: String) extends GameException(s"Incorrect input: $input.")

  final case class IncorrectParsingException(input: String) extends GameException(s"Incorrect parsing input (must be a number): $input.")

  final case class UserNotFoundException(user: User) extends GameException(s"User with name ${user.value} not found.")

  final case class NotEnoughMoneyException(available: BigDecimal, required: BigDecimal)
    extends GameException(s"Not enough money to buy. Required: $required, but available: $available.")

  final case class ValueParseException(value: Option[String], err: Throwable)
    extends GameException(s"Not able to parse player value: $value, because of exception: $err.")

  final case class SharesNumberException(newShares: Int)
    extends GameException(s"Incorrect number of shares after transaction (new shares number = $newShares). Total must be between 0 and 100.")

  final case class PlayerMarketValueNotFoundException(playerId: PlayerId, err: String)
    extends GameException(s"Market value for player $playerId not found. The reason is: $err")

  final case class PlayerSearchByNameException(playerName: String, err: String)
    extends GameException(s"Could not find player by name [$playerName]. The reason is: $err")

  final case class PlayerProfileNotFoundException(playerId: Int, err: String)
    extends GameException(s"Player profile for player with id [$playerId] not found. The reason is: $err")

  final case class UserAlreadyExistsException(user: User)
    extends GameException(s"User with name ${user.value} already exists.")

  final case class PlayerJsonNotFoundInMemoryException(playerId: PlayerId)
    extends GameException(s"Player profile JSON for player $playerId not found in memory.")

  final case class PlayerJsonNotFoundInMemoryCacheException(playerId: PlayerId)
    extends GameException(s"Player profile JSON for player $playerId not found in memory cache.")

  final case class PlayerJsonDecodingException(decodingFailure: DecodingFailure)
    extends GameException(s"Player JSON decoding failure: $decodingFailure")

  final case class PlayerProfileClientException(cause: String)
    extends GameException(s"Exception while invoking PlayerProfileClient. Message: $cause")

  final case class DynamoReaderException(cause: String)
  extends GameException(s"Exception while reading from DynamoDb. Message: $cause")

  final case class JsonParsingFailure(cause: String)
    extends GameException(s"Exception while parsing json from string. Message: $cause")
}