package utils

trait TimeProvider[F[_]] {
  def getSystemNanoTime: Long
}

object TimeProvider {

  def impl[F[_]]: TimeProvider[F] = new TimeProvider[F] {
    override def getSystemNanoTime: Long = System.nanoTime()
  }

}
