package game.player.client.domain

import io.circe.{Decoder, Encoder}

final case class FetchedStat(
                              competitionID: Option[String],
                              clubID: Option[Int],
                              seasonID: Option[String],
                              competitionName: Option[String],
                              appearances: Option[Int],
                              goals: Option[Int],
                              yellowCards: Option[Int],
                              minutesPlayed: Option[String]
)

object FetchedStat {

  implicit val decoder: Decoder[FetchedStat] = Decoder
    .forProduct8[
      FetchedStat,
      Option[String],
      Option[Int],
      Option[String],
      Option[String],
      Option[Int],
      Option[Int],
      Option[Int],
      Option[String]
    ](
      "competitionID",
      "clubID",
      "seasonID",
      "competitionName",
      "appearances",
      "goals",
      "yellowCards",
      "minutesPlayed"
    )(FetchedStat.apply)

  implicit val encoder: Encoder[FetchedStat] = Encoder
    .forProduct8[
      FetchedStat,
      Option[String],
      Option[Int],
      Option[String],
      Option[String],
      Option[Int],
      Option[Int],
      Option[Int],
      Option[String]
    ](
      "competitionID",
      "clubID",
      "seasonID",
      "competitionName",
      "appearances",
      "goals",
      "yellowCards",
      "minutesPlayed"
    )(Function.unlift(FetchedStat.unapply))

}
