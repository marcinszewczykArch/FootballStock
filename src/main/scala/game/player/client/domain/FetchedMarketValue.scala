package game.player.client.domain

import io.circe.Decoder
import io.circe.Encoder

final case class FetchedMarketValue(
  age: Option[Int],
  date: Option[String],
  clubName: Option[String],
  value: Option[String],
  clubId: Option[Int]
)

object FetchedMarketValue {

  implicit val decoder: Decoder[FetchedMarketValue] = Decoder.forProduct5[FetchedMarketValue, Option[Int], Option[String], Option[String], Option[String], Option[Int]](
    "age",
    "date",
    "clubName",
    "value",
    "clubID"
  )(FetchedMarketValue.apply)

  implicit val encoder: Encoder[FetchedMarketValue] = Encoder.forProduct5[FetchedMarketValue, Option[Int], Option[String], Option[String], Option[String], Option[Int]](
    "age",
    "date",
    "clubName",
    "value",
    "clubID"
  )(Function.unlift(FetchedMarketValue.unapply))

}
