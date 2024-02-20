package game.modules.club.service.domain

final case class ClubLeague(
  id: String,
  name: String,
  countryID: Int,
  countryName: String,
  tier: String
)

object ClubLeague {
  val empty = ClubLeague("-", "-", 0, "-", "-")
}
