package game.player.service.domain

import java.time.Instant

final case class MarketValue(
                              value: BigDecimal,
                              updatedAt: Instant
)

object MarketValue {

}
