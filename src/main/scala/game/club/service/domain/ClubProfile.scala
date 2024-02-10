package game.club.service.domain

import java.time.Instant

final case class ClubProfile(
  id: ClubId,
  name: String,
)

object ClubProfile {}

