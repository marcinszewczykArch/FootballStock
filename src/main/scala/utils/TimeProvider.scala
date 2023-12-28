package utils

import java.time.Instant

trait TimeProvider[F[_]] {
  def getCurrentTimestamp: Instant
}

object TimeProvider {

  def impl[F[_]]: TimeProvider[F] = new TimeProvider[F] {
    override def getCurrentTimestamp: Instant = Instant.now()
  }

}
