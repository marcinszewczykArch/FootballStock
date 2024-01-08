package game.gameState

case class User(value: String) extends AnyVal

object User {
  def apply(value: String): User = new User(value.toUpperCase)
}
