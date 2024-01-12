package http.security

import cats.effect.IO
import http.security.errors.Failure
import http.security.errors.Unauthorized

trait TokenVerification[F[_]] {

  def verify[E](role: RoleSelection, token: Secret[String]): F[Either[Failure[E], Unit]]

}

object TestTokenVerification extends TokenVerification[IO] {

  override def verify[E](role: RoleSelection, token: Secret[String]): IO[Either[Failure[E], Unit]] =
    IO(Right(()))

}

object EloTokenVerification extends TokenVerification[IO] {

  override def verify[E](role: RoleSelection, token: Secret[String]): IO[Either[Failure[E], Unit]] =
    if (token.value == "elo")
      IO(Right(()))
    else
      IO(Left(Unauthorized("Insufficient privileges or invalid scope")))

}
