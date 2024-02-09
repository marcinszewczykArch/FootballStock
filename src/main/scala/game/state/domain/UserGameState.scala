package game.state.domain

import game.player.service.domain.PlayerId
import io.circe.generic.semiauto.deriveDecoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.Decoder
import io.circe.Encoder
import io.circe.KeyDecoder
import io.circe.KeyEncoder

import java.time.Instant

final case class UserGameState(
  //todo: add user: User,
  portfolio: Map[PlayerId, StockInfo],
  money: BigDecimal,
  updatedAt: Instant //as versionNumber to process optimistic locking
)

object UserGameState {

  val initialCash: BigDecimal = BigDecimal(1_000_000) //todo: from config?

  implicit val userGameStateDecoder: Decoder[UserGameState] = deriveDecoder[UserGameState]
  implicit val userGameStateEncoder: Encoder[UserGameState] = deriveEncoder[UserGameState]

  implicit val keyEncoder: KeyEncoder[PlayerId] = KeyEncoder.instance[PlayerId](_.value.toString)
  implicit val keyDecoder: KeyDecoder[PlayerId] = KeyDecoder.instance[PlayerId](_.toIntOption.map(PlayerId(_)))

  implicit val mapEncoder: Encoder[Map[PlayerId, StockInfo]] = Encoder.encodeMap[PlayerId, StockInfo]
  implicit val mapDecoder: Decoder[Map[PlayerId, StockInfo]] = Decoder.decodeMap[PlayerId, StockInfo]

}

final case class StockInfo(
                            shares: List[Shares],
                            lastPlayerValue: BigDecimal
//                            lastPlayerClub: String //todo: to ClubId
                          )

object StockInfo {

  implicit val stockInfoDecoder: Decoder[StockInfo] = deriveDecoder[StockInfo]
  implicit val stockInfoEncoder: Encoder[StockInfo] = deriveEncoder[StockInfo]

}
