package game.player.service.domain

import game.club.service.domain.ClubId

final case class PlayerSimple(
  id: PlayerId,
  name: String,
  position: String,
  club: String,
  clubId: ClubId,
  age: String,
  nationalities: List[String],
  marketValue: BigDecimal
)

object PlayerSimple {}
