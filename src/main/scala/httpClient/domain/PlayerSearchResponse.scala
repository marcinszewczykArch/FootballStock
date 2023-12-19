package httpClient.domain

import io.circe.{Decoder, Encoder}

final case class PlayerSearchResponse(result: List[PlayerSearch])

object PlayerSearchResponse {

  implicit val decoder: Decoder[PlayerSearchResponse] = Decoder.forProduct1[PlayerSearchResponse, List[PlayerSearch]](
    "results"
  )(PlayerSearchResponse.apply)

  implicit val encoder: Encoder[PlayerSearchResponse] = Encoder.forProduct1[PlayerSearchResponse, List[PlayerSearch]](
    "results"
  )(Function.unlift(PlayerSearchResponse.unapply))

}
