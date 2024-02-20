package game.modules.player.client.domain

import io.circe.Decoder
import io.circe.Encoder

final case class FetchedPlayerSimple(
  id: Option[Int],
  name: Option[String],
  position: Option[String],
  club: Option[FetchedPlayerClub],
  age: Option[String],
  nationalities: Option[List[String]],
  marketValue: Option[String]
)

object FetchedPlayerSimple {

  implicit val decoder: Decoder[FetchedPlayerSimple] =
    Decoder.forProduct7[FetchedPlayerSimple, Option[Int], Option[String], Option[String], Option[
      FetchedPlayerClub
    ], Option[String], Option[List[String]], Option[String]](
      "id",
      "name",
      "position",
      "club",
      "age",
      "nationalities",
      "marketValue"
    )(FetchedPlayerSimple.apply)

  implicit val encoder: Encoder[FetchedPlayerSimple] =
    Encoder.forProduct7[FetchedPlayerSimple, Option[Int], Option[String], Option[String], Option[
      FetchedPlayerClub
    ], Option[String], Option[List[String]], Option[String]](
      "id",
      "name",
      "position",
      "club",
      "age",
      "nationalities",
      "marketValue"
    )(Function.unlift(FetchedPlayerSimple.unapply))

}
