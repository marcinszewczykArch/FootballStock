package multiplayer
import java.time.Instant

final case class UserGameState(
                       startTimestamp: Instant = Instant.now(),
                       portfolio: Map[Int, Double] = Map.empty, //todo: [PlayerId -> PlayerShares]
                       money: BigDecimal = BigDecimal(1_000_000)
                     )
