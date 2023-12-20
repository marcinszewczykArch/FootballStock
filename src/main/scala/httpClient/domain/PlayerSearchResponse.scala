package httpClient.domain

import io.circe.{Decoder, Encoder}

final case class PlayerSearchResponse(result: List[FetchedPlayerSimple])

object PlayerSearchResponse {

  implicit val decoder: Decoder[PlayerSearchResponse] = Decoder.forProduct1[PlayerSearchResponse, List[FetchedPlayerSimple]](
    "results"
  )(PlayerSearchResponse.apply)

  implicit val encoder: Encoder[PlayerSearchResponse] = Encoder.forProduct1[PlayerSearchResponse, List[FetchedPlayerSimple]](
    "results"
  )(Function.unlift(PlayerSearchResponse.unapply))

}
