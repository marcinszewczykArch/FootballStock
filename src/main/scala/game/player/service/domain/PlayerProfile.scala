package game.player.service.domain

import java.time.Instant

final case class PlayerProfile(
  id: PlayerId,
  url: String,
  name: String,
  description: String,
  imageURL: String,
  dateOfBirth: String,
  citizenship: List[String],
  isRetired: Boolean,
  position: PlayerPosition,
  clubId: Int,
  club: String,
  marketValue: BigDecimal,
  updatedAt: Instant
)

final case class PlayerPosition(main: String, other: List[String])

object PlayerPosition {
  val empty = PlayerPosition("-", Nil)
}
