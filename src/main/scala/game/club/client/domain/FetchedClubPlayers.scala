package game.club.client.domain

import io.circe.{Decoder, Encoder}

final case class FetchedClubPlayers(
  id: Option[Int],
  players: Option[List[FetchedClubPlayer]],
  updatedAt: Option[String]
)

object FetchedClubPlayers {

  implicit val decoder: Decoder[FetchedClubPlayers] =
    Decoder.forProduct3[
      FetchedClubPlayers,
      Option[Int],
      Option[List[FetchedClubPlayer]],
      Option[String]](
      "id",
      "players",
      "updatedAt"
    )(FetchedClubPlayers.apply)

  implicit val encoder: Encoder[FetchedClubPlayers] = Encoder.forProduct3[
    FetchedClubPlayers,
    Option[Int],
    Option[List[FetchedClubPlayer]],
    Option[String]](
    "id",
    "players",
    "updatedAt"
  )(Function.unlift(FetchedClubPlayers.unapply))

}
