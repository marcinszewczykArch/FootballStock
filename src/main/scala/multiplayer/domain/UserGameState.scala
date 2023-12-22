package multiplayer.domain

import services.domain.PlayerId

import java.time.Instant

final case class UserGameState(
                       startTimestamp: Instant = Instant.now(),
                       portfolio: Map[PlayerId, List[Shares]] = Map.empty,
                       money: BigDecimal = BigDecimal(1_000_000)
                     )

object UserGameState {
  def empty = UserGameState()
}
