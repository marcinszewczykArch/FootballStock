package game.club.client.domain

import io.circe.{Decoder, Encoder}

final case class FetchedClub(id: Option[Int], name: Option[String])

object FetchedClub {

  implicit val decoder: Decoder[FetchedClub] = Decoder.forProduct2[FetchedClub, Option[Int], Option[String]](
    "id",
    "name"
  )(FetchedClub.apply)

  implicit val encoder: Encoder[FetchedClub] = Encoder.forProduct2[FetchedClub, Option[Int], Option[String]](
    "id",
    "name"
  )(Function.unlift(FetchedClub.unapply))

}
