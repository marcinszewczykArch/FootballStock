package httpClient.domain

import io.circe.Decoder
import io.circe.Encoder

final case class FetchedMarketValue(
  marketValue: Option[String],
  updatedAt: Option[String]
)

object FetchedMarketValue {

  implicit val decoder: Decoder[FetchedMarketValue] = Decoder.forProduct2[FetchedMarketValue, Option[String], Option[String]](
    "marketValue",
    "updatedAt"
  )(FetchedMarketValue.apply)

  implicit val encoder: Encoder[FetchedMarketValue] = Encoder.forProduct2[FetchedMarketValue, Option[String], Option[String]](
    "marketValue",
    "updatedAt"
  )(Function.unlift(FetchedMarketValue.unapply))

}
