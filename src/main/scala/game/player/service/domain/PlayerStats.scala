package game.player.service.domain

import java.time.Instant

final case class PlayerStats(
  id: PlayerId,
  stats: List[Stat],
  updatedAt: Instant
)
