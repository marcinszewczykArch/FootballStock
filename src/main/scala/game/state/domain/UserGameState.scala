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
  user: User,
  portfolio: List[StockInfo],
  money: BigDecimal,
  updatedAt: Instant, //as versionNumber to process optimistic locking
  wishlist: List[(PlayerId, Instant)]
)

object UserGameState {

  val initialCash: BigDecimal = BigDecimal(1_000_000) //todo: from config?

  implicit val userGameStateDecoder: Decoder[UserGameState] = deriveDecoder[UserGameState]
  implicit val userGameStateEncoder: Encoder[UserGameState] = deriveEncoder[UserGameState]

}

final case class StockInfo(
                            playerId: PlayerId,
                            shares: List[Shares],
                            lastPlayerValue: BigDecimal,
                            lastPlayerMinutesPlayed: Int //to  be removed
                          )

object StockInfo {

  implicit val stockInfoDecoder: Decoder[StockInfo] = deriveDecoder[StockInfo]
  implicit val stockInfoEncoder: Encoder[StockInfo] = deriveEncoder[StockInfo]

}
