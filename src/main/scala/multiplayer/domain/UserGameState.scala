package multiplayer.domain

import services.domain.PlayerId
import utils.TimeProvider

import java.time.Instant

final case class UserGameState(
  portfolio: Map[PlayerId, List[Shares]],
  money: BigDecimal,
  events: List[UserEvent]
)

object UserGameState {
  val initialCash: BigDecimal = BigDecimal(1_000_000) //todo: from config?

  def initial[F[_]](implicit timeProvider: TimeProvider[F]) = UserGameState(
    portfolio = Map.empty,
    money = initialCash,
    events = List(InitializeGameEvent(initialCash, timeProvider.getCurrentTimestamp))
  )

//  def withInitialBudget[F[_]](budget: BigDecimal)(implicit timeProvider: TimeProvider[F]) = UserGameState(
//    startTimestamp = timeProvider.getCurrentTimestamp,
//    portfolio = Map.empty,
//    money = budget
//  )

}
