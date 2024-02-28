package http.security

import cats.effect.IO
import game.GameEngine
import game.modules.login.domain.TokenData
import http.security.errors.Failure
import http.security.errors.Unauthorized
import io.circe.parser
import utils.Coder

import java.nio.charset.StandardCharsets

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

object TokenVerification {

  def impl(gameEngine: GameEngine[IO]): TokenVerification[IO] = new TokenVerification[IO] {

    override def verify[E](role: RoleSelection, token: Secret[String]): IO[Either[Failure[E], Unit]] = {
      val decodedToken = Coder.decodeString(token.value)
      val loginResp = parser.parse(decodedToken).map(_.as[TokenData])

      loginResp match {
        case Left(decodingFailure) => IO(Left(Unauthorized("Insufficient privileges or invalid scope: " + decodingFailure.getMessage())))
        case Right(maybeTokenData) =>
          maybeTokenData match {
            case Left(decodingFailure) =>
              IO(Left(Unauthorized("Insufficient privileges or invalid scope: " + decodingFailure.getMessage())))
            case Right(tokenData)      =>
              gameEngine.validateTokenFor(user = tokenData.user)(tokenData.token).flatMap {
                case Right(true) => IO(Right(()))
                case _           => IO(Left(Unauthorized("Insufficient privileges or invalid scope")))
              }
          }
      }

    }

  }

}
