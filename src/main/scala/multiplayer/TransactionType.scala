package multiplayer

sealed abstract class TransactionType(val name: String)

case object Sell extends TransactionType("SELL")
case object Buy extends TransactionType("BUY")