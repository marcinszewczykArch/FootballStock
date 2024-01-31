package game.player.client.domain

import io.circe.Decoder
import io.circe.Encoder

final case class FetchedMarketValueHistory(
  id: Option[Int],
  marketValue: Option[String],
  marketValueHistory: List[FetchedMarketValue],
  updatedAt: Option[String]
)

object FetchedMarketValueHistory {

  implicit val decoder: Decoder[FetchedMarketValueHistory] = Decoder.forProduct4[FetchedMarketValueHistory, Option[Int], Option[String], List[FetchedMarketValue], Option[String]](
    "id",
    "marketValue",
    "marketValueHistory",
    "updatedAt"
  )(FetchedMarketValueHistory.apply)

  implicit val encoder: Encoder[FetchedMarketValueHistory] = Encoder.forProduct4[FetchedMarketValueHistory, Option[Int], Option[String], List[FetchedMarketValue], Option[String]](
    "id",
    "marketValue",
    "marketValueHistory",
    "updatedAt"
  )(Function.unlift(FetchedMarketValueHistory.unapply))

}
