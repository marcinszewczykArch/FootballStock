package game.modules.club.service.domain

import java.time.Instant

final case class ClubPlayers(
  id: ClubId,
  players: List[ClubPlayer],
  updatedAt: Instant
)

object ClubPlayers {}

