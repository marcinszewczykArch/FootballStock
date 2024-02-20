package game.modules.player.service.domain

import game.modules.club.service.domain.ClubId

final case class Stat(
  competitionID: String,
  clubID: ClubId,
  seasonID: String,
  competitionName: String,
  appearances: Int,
  goals: Int,
  yellowCards: Int,
  minutesPlayed: Int
)
