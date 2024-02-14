package game.player.client.domain

import io.circe.{Decoder, Encoder}

final case class FetchedPlayerStats(
  id: Option[Int],
  stats: Option[List[FetchedStat]],
  updatedAt: Option[String]
)

object FetchedPlayerStats {

  implicit val decoder: Decoder[FetchedPlayerStats] = Decoder
    .forProduct3[
      FetchedPlayerStats,
      Option[Int],
      Option[List[FetchedStat]],
      Option[String]
    ](
      "id",
      "stats",
      "updatedAt"
    )(FetchedPlayerStats.apply)

  implicit val encoder: Encoder[FetchedPlayerStats] = Encoder
    .forProduct3[
      FetchedPlayerStats,
      Option[Int],
      Option[List[FetchedStat]],
      Option[String]
    ](
      "id",
      "stats",
      "updatedAt"
    )(Function.unlift(FetchedPlayerStats.unapply))

}
