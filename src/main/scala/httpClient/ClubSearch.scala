package httpClient

import io.circe.{Decoder, Encoder}

final case class ClubSearch(id: Option[Int], name: Option[String])

object ClubSearch {

  implicit val decoder: Decoder[ClubSearch] = Decoder.forProduct2[ClubSearch, Option[Int], Option[String]](
    "id",
    "name"
  )(ClubSearch.apply)

  implicit val encoder: Encoder[ClubSearch] = Encoder.forProduct2[ClubSearch, Option[Int], Option[String]](
    "id",
    "name"
  )(Function.unlift(ClubSearch.unapply))

}