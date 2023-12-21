package multiplayer.domain

import java.time.Instant

case class Shares(
                   number: Int,
                   buyPrice: BigDecimal,
                   buyTimestamp: Instant
)

object Shares {
  def totalValue(shares: List[Shares]): BigDecimal = shares.map { case Shares(shares, buyPrice, _) => shares * buyPrice }.sum

}
