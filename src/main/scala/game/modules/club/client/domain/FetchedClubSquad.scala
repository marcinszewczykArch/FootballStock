package game.modules.club.client.domain

import io.circe.Decoder
import io.circe.Encoder

final case class FetchedClubSquad(
  size: Option[Int],
  averageAge: Option[Double],
  foreigners: Option[Int],
  nationalTeamPlayers: Option[Int]
)

object FetchedClubSquad {

  implicit val decoder: Decoder[FetchedClubSquad] = Decoder.forProduct4[
    FetchedClubSquad,
    Option[Int],
    Option[Double],
    Option[Int],
    Option[Int]
  ](
    "size",
    "averageAge",
    "foreigners",
    "nationalTeamPlayers"
  )(FetchedClubSquad.apply)

  implicit val encoder: Encoder[FetchedClubSquad] = Encoder.forProduct4[
    FetchedClubSquad,
    Option[Int],
    Option[Double],
    Option[Int],
    Option[Int]
  ](
    "size",
    "averageAge",
    "foreigners",
    "nationalTeamPlayers"
  )(Function.unlift(FetchedClubSquad.unapply))

}
