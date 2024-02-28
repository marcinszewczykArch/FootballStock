package game.modules.login.domain

import game.modules.state.domain.User
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant

case class TokenData(
  user: User,
  token: String,
  start: Instant,
  end: Instant,
  roles: List[String]
)

object TokenData {

  implicit val tokenDataDecoder: Decoder[TokenData] = deriveDecoder
  implicit val tokenDataEncoder: Encoder[TokenData] = deriveEncoder

}
