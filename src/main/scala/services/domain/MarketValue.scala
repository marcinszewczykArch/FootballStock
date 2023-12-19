package services.domain

import java.time.Instant

final case class MarketValue(
  marketValue: BigDecimal, //todo: to Big Decimal with currency
  updatedAt: Instant
)

object MarketValue {

}
