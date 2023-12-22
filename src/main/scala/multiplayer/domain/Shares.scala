package multiplayer.domain

import java.time.Instant
import scala.annotation.tailrec

case class Shares(
                   number: Int,
                   buyPrice: BigDecimal,
                   buyTimestamp: Instant
)

object Shares {
  def totalValue(shares: List[Shares]): BigDecimal = shares.map { case Shares(shares, buyPrice, _) => shares * buyPrice }.sum

  implicit class SharesOps(shares: Option[List[Shares]]) {

    def |-|(sharesToMinus: Int) = minus(shares.getOrElse(Nil), sharesToMinus)
    def |+|(sharesToPlus: Shares): List[Shares] = shares.getOrElse(Nil) :+ sharesToPlus
    def sum = shares.getOrElse(Nil).map(_.number).sum

    @tailrec
    private def minus(currentShares: List[Shares], sharesToMinus: Int): List[Shares] = currentShares match {
      case Nil            => Nil
      case ::(head, tail) =>
        head.number - sharesToMinus > 0 match {
          case true  => Shares(head.number - sharesToMinus, head.buyPrice, head.buyTimestamp) +: tail
          case false => minus(tail, sharesToMinus - head.number)
        }
    }

  }

}
