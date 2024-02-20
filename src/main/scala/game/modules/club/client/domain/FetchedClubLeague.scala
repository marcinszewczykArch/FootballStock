package game.modules.club.client.domain

import io.circe.Decoder
import io.circe.Encoder

final case class FetchedClubLeague(
  id: Option[String],
  name: Option[String],
  countryID: Option[Int],
  countryName: Option[String],
  tier: Option[String]
)

object FetchedClubLeague {

  implicit val decoder: Decoder[FetchedClubLeague] = Decoder.forProduct5[
    FetchedClubLeague,
    Option[String],
    Option[String],
    Option[Int],
    Option[String],
    Option[String]
  ](
    "id",
    "name",
    "countryID",
    "countryName",
    "tier"
  )(FetchedClubLeague.apply)

  implicit val encoder: Encoder[FetchedClubLeague] = Encoder.forProduct5[
    FetchedClubLeague,
    Option[String],
    Option[String],
    Option[Int],
    Option[String],
    Option[String]
  ](
    "id",
    "name",
    "countryID",
    "countryName",
    "tier"
  )(Function.unlift(FetchedClubLeague.unapply))

}
