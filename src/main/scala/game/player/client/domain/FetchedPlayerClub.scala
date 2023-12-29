package game.player.client.domain

import io.circe.{Decoder, Encoder}

final case class FetchedPlayerClub(id: Option[Int], name: Option[String])

object FetchedPlayerClub {

  implicit val decoder: Decoder[FetchedPlayerClub] = Decoder.forProduct2[FetchedPlayerClub, Option[Int], Option[String]](
    "id",
    "name"
  )(FetchedPlayerClub.apply)

  implicit val encoder: Encoder[FetchedPlayerClub] = Encoder.forProduct2[FetchedPlayerClub, Option[Int], Option[String]](
    "id",
    "name"
  )(Function.unlift(FetchedPlayerClub.unapply))

}