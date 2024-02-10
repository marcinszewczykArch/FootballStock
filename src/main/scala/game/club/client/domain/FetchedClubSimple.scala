package game.club.client.domain

import io.circe.{Decoder, Encoder}

final case class FetchedClubSimple(id: Option[Int], name: Option[String])

object FetchedClubSimple {

  implicit val decoder: Decoder[FetchedClubSimple] = Decoder.forProduct2[FetchedClubSimple, Option[Int], Option[String]](
    "id",
    "name"
  )(FetchedClubSimple.apply)

  implicit val encoder: Encoder[FetchedClubSimple] = Encoder.forProduct2[FetchedClubSimple, Option[Int], Option[String]](
    "id",
    "name"
  )(Function.unlift(FetchedClubSimple.unapply))

}
