package game.modules.login.domain

import game.modules.state.domain.User
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class UserData(
  user: User,
  email: String,
  role: String //todo: to Role enum
)

object UserData {

  implicit val eventDecoder: Decoder[UserData] = deriveDecoder
  implicit val eventEncoder: Encoder[UserData] = deriveEncoder

}
