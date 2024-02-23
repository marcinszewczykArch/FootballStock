package http.login

import io.circe.Decoder
import io.circe.Encoder
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder

object domain {

  final case class LoginRequest(
                                 user: String,
                                 password: String
                               )

  object LoginRequest {
    implicit val loginRequestDecoder: Decoder[LoginRequest] = deriveDecoder
    implicit val loginRequestEncoder: Encoder[LoginRequest] = deriveEncoder
  }

}
