package game.gameState

import game.player.service.domain.PlayerId
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder, Json, KeyDecoder, KeyEncoder}
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveConfiguredCodec

import java.time.Instant

final case class UserGameState(
  portfolio: Map[PlayerId, List[Shares]],
  money: BigDecimal,
  updatedAt: Instant //as versionNumber to process optimistic locking
)

object UserGameState {
  import sttp.tapir.generic.auto._

  val initialCash: BigDecimal = BigDecimal(1_000_000) //todo: from config?

  implicit val userGameStateDecoder: Decoder[UserGameState] = deriveDecoder[UserGameState]
  implicit val userGameStateEncoder: Encoder[UserGameState] = deriveEncoder[UserGameState]

  implicit val keyEncoder: KeyEncoder[PlayerId] = KeyEncoder.instance[PlayerId](_.value.toString)
  implicit val keyDecoder: KeyDecoder[PlayerId] = KeyDecoder.instance[PlayerId](_.toIntOption.map(PlayerId(_)))

  implicit val mapEncoder: Encoder[Map[PlayerId, List[Shares]]] = Encoder.encodeMap[PlayerId, List[Shares]]
  implicit val mapDecoder: Decoder[Map[PlayerId, List[Shares]]] = Decoder.decodeMap[PlayerId, List[Shares]]

}
