package game.modules.player.service.domain

import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder

case class PlayerId(value: Int) extends AnyVal

object PlayerId {
  implicit val playerIdDecoder: Decoder[PlayerId] = deriveDecoder
  implicit val playerIdEncoder: Encoder[PlayerId] = deriveEncoder
  implicit val ordering: Ordering[PlayerId] = (x: PlayerId, y: PlayerId) => x.value - y.value
}
