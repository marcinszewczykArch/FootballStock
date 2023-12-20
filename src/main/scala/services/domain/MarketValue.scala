package services.domain

import java.time.Instant

final case class MarketValue(
  marketValue: BigDecimal,
  updatedAt: Instant
)

object MarketValue {

}
