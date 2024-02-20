package game.modules.club.client.domain

import io.circe.{Decoder, Encoder}

final case class ClubSearchResponse(result: List[FetchedClubSimple])

object ClubSearchResponse {

  implicit val decoder: Decoder[ClubSearchResponse] = Decoder.forProduct1[ClubSearchResponse, List[FetchedClubSimple]](
    "results"
  )(ClubSearchResponse.apply)

  implicit val encoder: Encoder[ClubSearchResponse] = Encoder.forProduct1[ClubSearchResponse, List[FetchedClubSimple]](
    "results"
  )(Function.unlift(ClubSearchResponse.unapply))

}
