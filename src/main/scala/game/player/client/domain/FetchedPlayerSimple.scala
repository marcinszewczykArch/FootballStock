package game.player.client.domain

import io.circe.{Decoder, Encoder}

final case class FetchedPlayerSimple(
  id: Option[Int],
  name: Option[String],
  position: Option[String],
  club: Option[FetchedPlayerClub],
  age: Option[String],
  nationality: Option[String],
  marketValue: Option[String]
)

object FetchedPlayerSimple {

  implicit val decoder: Decoder[FetchedPlayerSimple] = Decoder.forProduct7[FetchedPlayerSimple, Option[Int], Option[String], Option[String], Option[
    FetchedPlayerClub
  ], Option[String], Option[String], Option[String]](
    "id",
    "name",
    "position",
    "club",
    "age",
    "nationality",
    "marketValue"
  )(FetchedPlayerSimple.apply)

  implicit val encoder: Encoder[FetchedPlayerSimple] = Encoder.forProduct7[FetchedPlayerSimple, Option[Int], Option[String], Option[String], Option[
    FetchedPlayerClub
  ], Option[String], Option[String], Option[String]](
    "id",
    "name",
    "position",
    "club",
    "age",
    "nationality",
    "marketValue"
  )(Function.unlift(FetchedPlayerSimple.unapply))

}
