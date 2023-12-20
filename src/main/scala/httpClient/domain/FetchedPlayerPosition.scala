package httpClient.domain

import io.circe.{Decoder, Encoder}

final case class FetchedPlayerPosition(main: Option[String], other: Option[List[String]])

object FetchedPlayerPosition {

  implicit val decoder: Decoder[FetchedPlayerPosition] = Decoder.forProduct2[FetchedPlayerPosition, Option[String], Option[List[String]]](
    "main",
    "other"
  )(FetchedPlayerPosition.apply)

  implicit val encoder: Encoder[FetchedPlayerPosition] = Encoder.forProduct2[FetchedPlayerPosition, Option[String], Option[List[String]]](
    "main",
    "other"
  )(Function.unlift(FetchedPlayerPosition.unapply))

}