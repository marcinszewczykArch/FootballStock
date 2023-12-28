package multiplayer.domain

import services.domain.PlayerId
import utils.TimeProvider

import java.time.Instant

final case class UserGameState(
  startTimestamp: Instant,
  portfolio: Map[PlayerId, List[Shares]],
  money: BigDecimal
)

object UserGameState {
  val initialCash: BigDecimal = BigDecimal(1_000_000) //todo: from config?

  def empty[F[_]](implicit timeProvider: TimeProvider[F]) = UserGameState(
    startTimestamp = timeProvider.getCurrentTimestamp,
    portfolio = Map.empty,
    money = initialCash
  )

//  def withInitialBudget[F[_]](budget: BigDecimal)(implicit timeProvider: TimeProvider[F]) = UserGameState(
//    startTimestamp = timeProvider.getCurrentTimestamp,
//    portfolio = Map.empty,
//    money = budget
//  )

}
