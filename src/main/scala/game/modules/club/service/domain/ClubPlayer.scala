package game.modules.club.service.domain

import game.modules.player.service.domain.PlayerId

final case class ClubPlayer(
  id: PlayerId,
  name: String,
  position: String,
  dateOfBirth: String,
  age: Int,
  nationality: List[String],
  height: String,
  foot: String,
  joinedOn: String,
  joined: String,
  signedFrom: String,
  contract: String,
  marketValue: BigDecimal
)

object ClubPlayer {


}
