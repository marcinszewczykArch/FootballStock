package multiplayer.domain

sealed abstract class TransactionType(val name: String)

object TransactionType {
  case object Sell extends TransactionType("SELL")
  case object Buy extends TransactionType("BUY")
}
