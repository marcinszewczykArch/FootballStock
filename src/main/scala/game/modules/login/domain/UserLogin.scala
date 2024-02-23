package game.modules.login.domain

import game.modules.state.domain.User
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class UserLogin(
  user: User,
  hash: String,
  email: String,
  role: String
)

object UserLogin {

  implicit val eventDecoder: Decoder[UserLogin] = deriveDecoder
  implicit val eventEncoder: Encoder[UserLogin] = deriveEncoder

}
