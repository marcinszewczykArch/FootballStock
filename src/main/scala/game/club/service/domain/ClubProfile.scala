package game.club.service.domain

import java.time.Instant

final case class ClubProfile(
  id: ClubId,
  url: String,
  name: String,
  officialName: String,
  image: String,
  website: String,
  foundedOn: String,
  stadiumName: String,
  stadiumSeats: Int,
  currentMarketValue: BigDecimal,
  squad: ClubSquad,
  league: ClubLeague,
  updatedAt: Instant
)

object ClubProfile {}
