package game.modules.club.client.domain

import io.circe.{Decoder, Encoder}

final case class FetchedClubSimple(
  id: Option[Int],
  url: Option[String],
  name: Option[String],
  country: Option[String],
  squad: Option[Int],
  marketValue: Option[String]
)

object FetchedClubSimple {

  implicit val decoder: Decoder[FetchedClubSimple] = Decoder.forProduct6[
    FetchedClubSimple,
    Option[Int],
    Option[String],
    Option[String],
    Option[String],
    Option[Int],
    Option[String],
  ](
    "id",
    "url",
    "name",
    "country",
    "squad",
    "marketValue"
  )(FetchedClubSimple.apply)

  implicit val encoder: Encoder[FetchedClubSimple] = Encoder.forProduct6[
    FetchedClubSimple,
    Option[Int],
    Option[String],
    Option[String],
    Option[String],
    Option[Int],
    Option[String],
  ](
    "id",
    "url",
    "name",
    "country",
    "squad",
    "marketValue"
  )(Function.unlift(FetchedClubSimple.unapply))

}
