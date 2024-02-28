package game.modules.login.domain

import game.modules.state.domain.User
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

import java.time.Instant

case class UserLoginResponse(
                              user: User,
                              token: String,
                              start: Instant,
                              end: Instant,
                              roles: List[String]
)

object UserLoginResponse {

  implicit val userLoginResponseDecoder: Decoder[UserLoginResponse] = deriveDecoder
  implicit val userLoginResponseEncoder: Encoder[UserLoginResponse] = deriveEncoder

}
