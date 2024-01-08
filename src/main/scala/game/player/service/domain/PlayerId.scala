package game.player.service.domain

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

case class PlayerId (value: Int) extends AnyVal

object PlayerId {
  implicit val playerIdDecoder: Decoder[PlayerId] = deriveDecoder
  implicit val playerIdEncoder: Encoder[PlayerId] = deriveEncoder
}
