package game.player.service.domain

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class PlayerId(value: Int) extends AnyVal

object PlayerId {
  implicit val playerIdDecoder: Decoder[PlayerId] = deriveDecoder
  implicit val playerIdEncoder: Encoder[PlayerId] = deriveEncoder
}
