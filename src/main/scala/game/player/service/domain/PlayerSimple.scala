package game.player.service.domain

final case class PlayerSimple(
  id: PlayerId,
  name: String,
  position: String,
  club: String,
  age: String,
  nationalities: List[String],
  marketValue: BigDecimal
)

object PlayerSimple {}
