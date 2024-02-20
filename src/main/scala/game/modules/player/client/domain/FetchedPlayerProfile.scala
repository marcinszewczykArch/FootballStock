package game.modules.player.client.domain

import io.circe.Decoder
import io.circe.Encoder

final case class FetchedPlayerProfile(
  id: Option[String],
  url: Option[String],
  name: Option[String],
  description: Option[String],
  imageURL: Option[String],
  dateOfBirth: Option[String],
  citizenship: Option[Seq[String]],
  isRetired: Option[Boolean],
  position: Option[FetchedPlayerPosition],
  club: Option[FetchedPlayerClub],
  marketValue: Option[String],
  updatedAt: Option[String]
)

object FetchedPlayerProfile {

  implicit val decoder: Decoder[FetchedPlayerProfile] = Decoder
    .forProduct12[
      FetchedPlayerProfile,
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[Seq[String]],
      Option[Boolean],
      Option[FetchedPlayerPosition],
      Option[FetchedPlayerClub],
      Option[String],
      Option[String]
    ](
      "id",
      "url",
      "name",
      "description",
      "imageURL",
      "dateOfBirth",
      "citizenship",
      "isRetired",
      "position",
      "club",
      "marketValue",
      "updatedAt"
    )(FetchedPlayerProfile.apply)

  implicit val encoder: Encoder[FetchedPlayerProfile] = Encoder
    .forProduct12[
      FetchedPlayerProfile,
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[String],
      Option[Seq[String]],
      Option[Boolean],
      Option[FetchedPlayerPosition],
      Option[FetchedPlayerClub],
      Option[String],
      Option[String]
    ](
      "id",
      "url",
      "name",
      "description",
      "imageURL",
      "dateOfBirth",
      "citizenship",
      "isRetired",
      "position",
      "club",
      "marketValue",
      "updatedAt"
    )(Function.unlift(FetchedPlayerProfile.unapply))

}
