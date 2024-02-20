package game.modules.player.service.domain

import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}

import java.time.Instant

final case class MarketValue(
  age: Int,
  date: Instant,
  clubName: String,
  value: BigDecimal,
  clubId: Int
)

object MarketValue {

  implicit val marketValueDecoder: Decoder[MarketValue] = deriveDecoder
  implicit val marketValueEncoder: Encoder[MarketValue] = deriveEncoder

}
