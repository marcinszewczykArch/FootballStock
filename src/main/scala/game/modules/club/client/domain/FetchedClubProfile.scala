package game.modules.club.client.domain

import io.circe.Decoder
import io.circe.Encoder

final case class FetchedClubProfile(
  id: Option[Int],
  url: Option[String],
  name: Option[String],
  officialName: Option[String],
  image: Option[String],
  website: Option[String],
  foundedOn: Option[String],
  stadiumName: Option[String],
  stadiumSeats: Option[Int],
  currentMarketValue: Option[String],
  squad: Option[FetchedClubSquad],
  league: Option[FetchedClubLeague],
  updatedAt: Option[String]
)

object FetchedClubProfile {

  implicit val decoder: Decoder[FetchedClubProfile] = Decoder.forProduct13[
    FetchedClubProfile,
    Option[Int],
    Option[String],
    Option[String],
    Option[String],
    Option[String],
    Option[String],
    Option[String],
    Option[String],
    Option[Int],
    Option[String],
    Option[FetchedClubSquad],
    Option[FetchedClubLeague],
    Option[String]
  ](
    "id",
    "url",
    "name",
    "officialName",
    "image",
    "website",
    "foundedOn",
    "stadiumName",
    "stadiumSeats",
    "currentMarketValue",
    "squad",
    "league",
    "updatedAt"
  )(FetchedClubProfile.apply)

  implicit val encoder: Encoder[FetchedClubProfile] = Encoder.forProduct13[
    FetchedClubProfile,
    Option[Int],
    Option[String],
    Option[String],
    Option[String],
    Option[String],
    Option[String],
    Option[String],
    Option[String],
    Option[Int],
    Option[String],
    Option[FetchedClubSquad],
    Option[FetchedClubLeague],
    Option[String]
  ](
    "id",
    "url",
    "name",
    "officialName",
    "image",
    "website",
    "foundedOn",
    "stadiumName",
    "stadiumSeats",
    "currentMarketValue",
    "squad",
    "league",
    "updatedAt"
  )(Function.unlift(FetchedClubProfile.unapply))

}
