package game.club.service.domain

final case class ClubSquad(
  size: Int,
  averageAge: Double,
  foreigners: Int,
  nationalTeamPlayers: Int
)

object ClubSquad {
  val empty = ClubSquad(0, 0, 0, 0)
}
