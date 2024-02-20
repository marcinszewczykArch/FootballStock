package game.modules.player.service.domain

import game.modules.club.service.domain.ClubId

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
