package services.domain

import java.time.Instant

final case class MarketValue(
                              value: BigDecimal,
                              updatedAt: Instant
)

object MarketValue {

}
