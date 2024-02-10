package game.club.service.domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class ClubId(value: Int) extends AnyVal

object ClubId {
  implicit val clubIdDecoder: Decoder[ClubId] = deriveDecoder
  implicit val clubIdEncoder: Encoder[ClubId] = deriveEncoder
}
