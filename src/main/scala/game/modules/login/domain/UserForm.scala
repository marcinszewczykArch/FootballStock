package game.modules.login.domain

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class UserForm(
  user: String,
  password: String,
  email: String
)

object UserForm {

  implicit val eventDecoder: Decoder[UserForm] = deriveDecoder
  implicit val eventEncoder: Encoder[UserForm] = deriveEncoder

}
