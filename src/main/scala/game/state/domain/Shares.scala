package game.state.domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.Instant
import scala.annotation.tailrec

case class Shares(
  number: Int,
  buyPrice: BigDecimal,
  buyTimestamp: Instant,
  buyMinutesPlayed: Int,
  minutesPlayedLastSeen: Int,
  dividend: BigDecimal
)

object Shares {
  implicit val sharesDecoder: Decoder[Shares] = deriveDecoder
  implicit val sharesEncoder: Encoder[Shares] = deriveEncoder

  def totalValue(shares: List[Shares]): BigDecimal = shares.map { case Shares(shares, buyPrice, _, _, _, _) => shares * buyPrice }.sum

  implicit class SharesOps(shares: Option[List[Shares]]) {

    def |-|(sharesToMinus: Int)                 = minus(shares.getOrElse(Nil), sharesToMinus)
    def |+|(sharesToPlus: Shares): List[Shares] = shares.getOrElse(Nil) :+ sharesToPlus
    def sum                                     = shares.getOrElse(Nil).map(_.number).sum

    @tailrec
    private def minus(currentShares: List[Shares], sharesToMinus: Int): List[Shares] = currentShares match {
      case Nil            => Nil
      case ::(head, tail) =>
        head.number - sharesToMinus > 0 match {
          case true  => Shares(head.number - sharesToMinus, head.buyPrice, head.buyTimestamp, head.buyMinutesPlayed, head.minutesPlayedLastSeen, head.dividend) +: tail
          case false => minus(tail, sharesToMinus - head.number)
        }
    }

  }

}
