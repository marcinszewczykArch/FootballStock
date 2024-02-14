package game.player.service.domain

final case class Stat(
  competitionID: String,
  clubID: Int,
  seasonID: String,
  competitionName: String,
  appearances: Int,
  goals: Int,
  yellowCards: Int,
  minutesPlayed: Int
)
