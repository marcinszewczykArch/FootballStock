package httpClient

import io.circe.{Decoder, Encoder}

final case class PlayerSearch(
  id: Option[Int],
  name: Option[String],
  position: Option[String],
  club: Option[ClubSearch],
  age: Option[String],
  nationality: Option[String],
  marketValue: Option[String]
)

object PlayerSearch {

  implicit val decoder: Decoder[PlayerSearch] = Decoder.forProduct7[PlayerSearch, Option[Int], Option[String], Option[String], Option[
    ClubSearch
  ], Option[String], Option[String], Option[String]](
    "id",
    "name",
    "position",
    "club",
    "age",
    "nationality",
    "marketValue"
  )(PlayerSearch.apply)

  implicit val encoder: Encoder[PlayerSearch] = Encoder.forProduct7[PlayerSearch, Option[Int], Option[String], Option[String], Option[
    ClubSearch
  ], Option[String], Option[String], Option[String]](
    "id",
    "name",
    "position",
    "club",
    "age",
    "nationality",
    "marketValue"
  )(Function.unlift(PlayerSearch.unapply))

}
