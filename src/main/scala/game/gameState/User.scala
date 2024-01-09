package game.gameState

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class User(value: String) extends AnyVal

object User {
  def apply(value: String): User = new User(value.toUpperCase)

  implicit val userDecoder: Decoder[User] = deriveDecoder
  implicit val userEncoder: Encoder[User] = deriveEncoder
}
