package game.modules.club.service.domain

final case class ClubSimple(
  id: ClubId,
  url: String,
  name: String,
  country: String,
  squad: Int,
  marketValue: String
)

object ClubSimple {}
