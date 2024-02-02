package game.player.service.domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.Instant

final case class MarketValueHistory(
  id: PlayerId,
  marketValue: BigDecimal,
  marketValueHistory: List[MarketValue],
  updatedAt: Instant
)

object MarketValueHistory {

  implicit val marketValueHistoryDecoder: Decoder[MarketValueHistory] = deriveDecoder
  implicit val marketValueHistoryEncoder: Encoder[MarketValueHistory] = deriveEncoder

}
