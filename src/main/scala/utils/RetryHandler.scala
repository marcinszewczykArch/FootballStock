package utils

import cats.effect.Async
import cats.implicits.catsSyntaxApplicativeError
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object RetryHandler {

  /** Retry the wrappedAction if it fails.
    *
    * @param maxRetryAttempt maximum number of wrappedAction retries after the initial attempt fails
    * @param delay           delay time for the next attempt if the previous one fails
    * @param wrappedAction   action to be executed with retry if fails
    */

  def runRetry[F[_]: Async, A](
    maxRetryAttempt: Int,
    delay: FiniteDuration = 100.milliseconds
  )(
    wrappedAction: F[A]
  )(
    implicit log: Logger[F]
  ): F[A] = fs2
    .Stream
    .retry(
      wrappedAction.onError { case err: Throwable =>
        log.warn(s"Wrapped action failed and will be retried $maxRetryAttempt times: " + err.getMessage)
      },
      delay,
      identity,
      maxRetryAttempt
    )
    .compile
    .lastOrError

}
