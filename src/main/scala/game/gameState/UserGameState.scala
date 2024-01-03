package game.gameState

import game.player.service.domain.PlayerId

final case class UserGameState(
  portfolio: Map[PlayerId, List[Shares]],
  money: BigDecimal
)

object UserGameState {
  val initialCash: BigDecimal = BigDecimal(1_000_000) //todo: from config?
}
