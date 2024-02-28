package game.modules.login.memory

import cats.Applicative
import cats.effect._
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxApplyOps, toFunctorFilterOps, toTraverseOps}
import game.GameException
import game.GameException.{DynamoReaderException, JsonDecodingException, JsonParsingFailure}
import game.modules.login.domain.{TokenData, UserLogin}
import game.modules.state.domain.User
import io.circe.parser
import io.circe.syntax.EncoderOps
import org.scanamo.Scanamo
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.Type.ErrorOr

import java.time.Instant

trait TokenMemory[F[_]] {

  def addToken(token: TokenData): F[Unit]
  def findTokenData(token: String, user: User): F[ErrorOr[TokenData]]
}

object TokenMemory {

  def impl[F[_]: Sync: LoggerFactory](scanamo: Scanamo): TokenMemory[F] =
    new TokenMemory[F] {

      import org.scanamo._
      import org.scanamo.generic.auto._
      import org.scanamo.syntax._

      implicit val log: SelfAwareStructuredLogger[F] =
        LoggerFactory.getLoggerFromName[F](classOf[LoginMemory[F]].getName)

      private val table = Table[TokenTable]("Token")
      private case class TokenTable(token: String, user: String, start: Instant, end: Instant)

      override def addToken(tokenData: TokenData): F[Unit] =
        log.debug(s"adding new token for ${tokenData.user} to dynamoDb...") *> scanamo
          .exec(
            table.put(
              TokenTable(
                user = tokenData.user.value,
                token = tokenData.token,
                start = tokenData.start,
                end = tokenData.end
              )
            )
          )
          .pure

      override def findTokenData(token: String, user: User): F[Either[GameException, TokenData]] =
        log.debug(s"checking token in dynamoDb") *>
          (scanamo
            .exec(table.get("token" === token and "user" === user.value))
            .map(_.left.map(err => DynamoReaderException(err.toString))) match {
            case Some(record) => Applicative[F].pure(record.map(toTokenData))
            case None         => Applicative[F].pure(Left[GameException, TokenData](DynamoReaderException(s"Token not found in memory.")))
          })

      private def toTokenData(record: TokenTable): TokenData =
        TokenData(
          user = User(record.user),
          token = record.token,
          start = record.start,
          end = record.end,
          roles = Nil
        )

    }

}
