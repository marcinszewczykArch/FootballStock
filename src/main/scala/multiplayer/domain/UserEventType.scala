package multiplayer.domain

sealed abstract class UserEventType(val name: String)

object UserEventType {
  case object InitializeGame extends UserEventType("INITIALIZE_GAME")
  case object Sell extends UserEventType("SELL")
  case object Buy extends UserEventType("BUY")
}
