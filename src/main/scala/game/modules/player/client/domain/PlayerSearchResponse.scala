package game.modules.player.client.domain

import io.circe.Decoder
import io.circe.Encoder

final case class PlayerSearchResponse(result: List[FetchedPlayerSimple])

object PlayerSearchResponse {

  implicit val decoder: Decoder[PlayerSearchResponse] = Decoder.forProduct1[PlayerSearchResponse, List[FetchedPlayerSimple]](
    "results"
  )(PlayerSearchResponse.apply)

  implicit val encoder: Encoder[PlayerSearchResponse] = Encoder.forProduct1[PlayerSearchResponse, List[FetchedPlayerSimple]](
    "results"
  )(Function.unlift(PlayerSearchResponse.unapply))

}
