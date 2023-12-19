package httpClient.domain

import io.circe.{Decoder, Encoder}

final case class PlayerDetails(
  id: Option[Int],
  name: Option[String],
  position: Option[String],
  club: Option[ClubSearch],
  age: Option[String],
  nationality: Option[String],
  marketValue: Option[String]
)

object PlayerDetails {

  implicit val decoder: Decoder[PlayerDetails] = Decoder.forProduct7[PlayerDetails, Option[Int], Option[String], Option[String], Option[
    ClubSearch
  ], Option[String], Option[String], Option[String]](
    "id",
    "name",
    "position",
    "club",
    "age",
    "nationality",
    "marketValue"
  )(PlayerDetails.apply)

  implicit val encoder: Encoder[PlayerDetails] = Encoder.forProduct7[PlayerDetails, Option[Int], Option[String], Option[String], Option[
    ClubSearch
  ], Option[String], Option[String], Option[String]](
    "id",
    "name",
    "position",
    "club",
    "age",
    "nationality",
    "marketValue"
  )(Function.unlift(PlayerDetails.unapply))

}
