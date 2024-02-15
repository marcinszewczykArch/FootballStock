package game.player.service.domain

import game.club.service.domain.ClubId

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
