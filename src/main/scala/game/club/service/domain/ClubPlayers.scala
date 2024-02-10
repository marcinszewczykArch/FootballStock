package game.club.service.domain

final case class ClubPlayers(
  id: ClubId,
  players: List[ClubPlayer],
)

object ClubPlayers {}

