package game.club.client.domain

import io.circe.Decoder
import io.circe.Encoder

final case class FetchedClubPlayer(
  id: Option[Int],
  name: Option[String],
  position: Option[String],
  dateOfBirth: Option[String],
  age: Option[Int],
  nationality: Option[List[String]],
  height: Option[String],
  foot: Option[String],
  joinedOn: Option[String],
  joined: Option[String],
  signedFrom: Option[String],
  contract: Option[String],
  marketValue: Option[String]
)

object FetchedClubPlayer {

  implicit val decoder: Decoder[FetchedClubPlayer] =
    Decoder.forProduct13[
      FetchedClubPlayer,
      Option[Int],
      Option[String],
      Option[String],
      Option[String],
      Option[Int],
      Option[List[String]],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String]](
      "id",
      "name",
      "position",
      "dateOfBirth",
      "age",
      "nationality",
      "height",
      "foot",
      "joinedOn",
      "joined",
      "signedFrom",
      "contract",
      "marketValue"
    )(FetchedClubPlayer.apply)

  implicit val encoder: Encoder[FetchedClubPlayer] = Encoder.forProduct13[
    FetchedClubPlayer,
    Option[Int],
    Option[String],
    Option[String],
    Option[String],
    Option[Int],
    Option[List[String]],
    Option[String],
    Option[String],
    Option[String],
    Option[String],
    Option[String],
    Option[String],
    Option[String]
  ](
    "id",
    "name",
    "position",
    "dateOfBirth",
    "age",
    "nationality",
    "height",
    "foot",
    "joinedOn",
    "joined",
    "signedFrom",
    "contract",
    "marketValue"
  )(Function.unlift(FetchedClubPlayer.unapply))

}
