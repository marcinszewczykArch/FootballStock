package game.errors

import game.player.service.domain.PlayerId
import game.state.domain.User
import http.security.CirceExtraConfiguration
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import utils.CurrencyFormatter.toEuroString

sealed trait GameException extends Throwable with Product with Serializable

object GameException extends CirceExtraConfiguration {

  final case class IncorrectConsoleInputException(input: String) extends GameException {
    override def getMessage = s"Incorrect input: $input."
  }

  implicit val IncorrectConsoleInputExceptionCodec: Codec[IncorrectConsoleInputException] =
    deriveConfiguredCodec[IncorrectConsoleInputException]

  final case class IncorrectParsingException(input: String) extends GameException {
    override def getMessage = s"Incorrect parsing input (must be a number): $input."
  }

  implicit val IncorrectParsingExceptionCodec: Codec[IncorrectParsingException] = deriveConfiguredCodec[IncorrectParsingException]

  final case class UserNotFoundException(user: User) extends GameException {
    override def getMessage = s"User with name ${user.value} not found."
  }

  implicit val UserNotFoundExceptionCodec: Codec[UserNotFoundException] = deriveConfiguredCodec[UserNotFoundException]

  final case class NotEnoughMoneyException(available: BigDecimal, required: BigDecimal) extends GameException {
    override def getMessage = s"Not enough money to buy. Required: ${toEuroString(required)}, but available: ${toEuroString(available)}."
  }

  implicit val NotEnoughMoneyExceptionCodec: Codec[NotEnoughMoneyException] = deriveConfiguredCodec[NotEnoughMoneyException]

  final case class SharesNumberException(newShares: Int) extends GameException {
    override def getMessage =
      s"Incorrect number of shares after transaction (new shares number = $newShares). Total must be between 0 and 100."
  }

  implicit val SharesNumberExceptionCodec: Codec[SharesNumberException] = deriveConfiguredCodec[SharesNumberException]

  final case class PlayerMarketValueNotFoundException(playerId: PlayerId, err: String) extends GameException {
    override def getMessage = s"Market value for player $playerId not found. The reason is: $err"
  }

  implicit val PlayerMarketValueNotFoundExceptionCodec: Codec[PlayerMarketValueNotFoundException] =
    deriveConfiguredCodec[PlayerMarketValueNotFoundException]

  final case class PlayerMarketValueNotUpToDateException(playerId: PlayerId, displayedValue: BigDecimal, realValue: BigDecimal) extends GameException {
    override def getMessage = s"Displayed market value for player $playerId is not up-to-date. " +
      s"Displayed value: ${toEuroString(displayedValue)}, updated value: ${toEuroString(realValue)}"
  }

  implicit val playerMarketValueNotUpToDateExceptionCodec: Codec[PlayerMarketValueNotUpToDateException] =
    deriveConfiguredCodec[PlayerMarketValueNotUpToDateException]

  final case class PlayerSearchByNameException(playerName: String, err: String) extends GameException {
    override def getMessage = s"Could not find player by name [$playerName]. The reason is: $err"
  }

  implicit val PlayerSearchByNameExceptionCodec: Codec[PlayerSearchByNameException] = deriveConfiguredCodec[PlayerSearchByNameException]

  final case class PlayerProfileNotFoundException(playerId: Int, err: String) extends GameException {
    override def getMessage = s"Player profile for player with id [$playerId] not found. The reason is: $err"
  }

  implicit val PlayerProfileNotFoundExceptionCodec: Codec[PlayerProfileNotFoundException] =
    deriveConfiguredCodec[PlayerProfileNotFoundException]

  final case class UserAlreadyExistsException(user: User) extends GameException {
    override def getMessage = s"User with name ${user.value} already exists."
  }

  implicit val UserAlreadyExistsExceptionCodec: Codec[UserAlreadyExistsException] = deriveConfiguredCodec[UserAlreadyExistsException]

  final case class PlayerJsonNotFoundInMemoryException(playerId: PlayerId) extends GameException {
    override def getMessage = s"Player profile JSON for player $playerId not found in memory."
  }

  implicit val PlayerJsonNotFoundInMemoryExceptionCodec: Codec[PlayerJsonNotFoundInMemoryException] =
    deriveConfiguredCodec[PlayerJsonNotFoundInMemoryException]

  final case class PlayerJsonNotFoundInMemoryCacheException(playerId: PlayerId) extends GameException {
    override def getMessage = s"Player profile JSON for player $playerId not found in memory cache."
  }

  implicit val PlayerJsonNotFoundInMemoryCacheExceptionCodec: Codec[PlayerJsonNotFoundInMemoryCacheException] =
    deriveConfiguredCodec[PlayerJsonNotFoundInMemoryCacheException]

  final case class JsonDecodingException(cause: String) extends GameException { override def getMessage = s"JSON decoding failure: $cause" }
  implicit val JsonDecodingExceptionCodec: Codec[JsonDecodingException] = deriveConfiguredCodec[JsonDecodingException]

  final case class PlayerProfileClientException(cause: String) extends GameException {
    override def getMessage = s"Exception while invoking PlayerProfileClient. Message: $cause"
  }

  implicit val PlayerProfileClientExceptionCodec: Codec[PlayerProfileClientException] = deriveConfiguredCodec[PlayerProfileClientException]

  final case class PlayerMarketValueHistoryClientException(cause: String) extends GameException {
    override def getMessage = s"Exception while invoking PlayerMarketValueHistoryClient. Message: $cause"
  }

  implicit val PlayerMarketValueHistoryClientExceptionCodec: Codec[PlayerMarketValueHistoryClientException] = deriveConfiguredCodec[PlayerMarketValueHistoryClientException]

  final case class PlayerMarketValueException(playerId: PlayerId, cause: String) extends GameException {
    override def getMessage = s"Exception while getting Player Market Value for $playerId. Message: $cause"
  }

  implicit val PlayerMarketValueExceptionCodec: Codec[PlayerMarketValueException] =
    deriveConfiguredCodec[PlayerMarketValueException]


  final case class DynamoReaderException(cause: String) extends GameException {
    override def getMessage = s"Exception while reading from DynamoDb. Message: $cause"
  }

  implicit val DynamoReaderExceptionCodec: Codec[DynamoReaderException] = deriveConfiguredCodec[DynamoReaderException]

  final case class JsonParsingFailure(cause: String) extends GameException {
    override def getMessage = s"Exception while parsing json from string. Message: $cause"
  }

  implicit val JsonParsingFailureCodec: Codec[JsonParsingFailure] = deriveConfiguredCodec[JsonParsingFailure]

  final case class DynamoDbUpdateException(cause: String) extends GameException {
    override def getMessage = s"Exception while updating record in DynamoDb. Message: $cause"
  }

  implicit val DynamoDbUpdateExceptionCodec: Codec[DynamoDbUpdateException] = deriveConfiguredCodec[DynamoDbUpdateException]

  implicit val gameExceptionCodec: Codec[GameException] = deriveConfiguredCodec[GameException]

}
