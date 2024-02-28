package game.modules.login.memory

import cats.Applicative
import cats.effect._
import cats.implicits.{catsSyntaxApplicativeId, catsSyntaxApplyOps, toFunctorFilterOps, toTraverseOps}
import game.GameException
import game.GameException.{DynamoReaderException, JsonDecodingException, JsonParsingFailure}
import game.modules.login.domain.UserLogin
import game.modules.state.domain.User
import io.circe.parser
import io.circe.syntax.EncoderOps
import org.scanamo.Scanamo
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.Type.ErrorOr

trait LoginMemory[F[_]] {

  def addUserLogin(login: UserLogin): F[Unit]
  def getUserLogin(user: User): F[ErrorOr[UserLogin]]
  def getAllUserLogins(): F[ErrorOr[List[UserLogin]]]
}

object LoginMemory {

  //todo: add cachedInstance

  def impl[F[_]: Sync: LoggerFactory](scanamo: Scanamo): LoginMemory[F] =
    new LoginMemory[F] {

      import org.scanamo._
      import org.scanamo.generic.auto._
      import org.scanamo.syntax._

      implicit val log: SelfAwareStructuredLogger[F] =
        LoggerFactory.getLoggerFromName[F](classOf[LoginMemory[F]].getName)

      private val table = Table[LoginTable]("Login")
      private case class LoginTable(user: String, json: String)

      override def addUserLogin(login: UserLogin): F[Unit] =
        log.debug(s"adding UserLogin for ${login.user} to dynamoDb...") *> scanamo
          .exec(
            table.put(
              LoginTable(
                user = login.user.value,
                json = login.asJson.toString
              )
            )
          )
          .pure

      override def getUserLogin(user: User): F[ErrorOr[UserLogin]] =
        log.debug(s"getting UserLogin for $user from dynamoDb") *>
          (scanamo
            .exec(table.get("user" === user.value))
            .map(_.left.map(err => DynamoReaderException(err.toString))) match {
            case Some(value) =>
              value
                .map(_.json)
                .flatMap(toUserLogin)
                .pure

            case None =>
              Applicative[F].pure(Left[GameException, UserLogin](DynamoReaderException(s"UserLogin for $user not found in memory.")))
          })

      override def getAllUserLogins(): F[ErrorOr[List[UserLogin]]] =
        log.debug(s"getting all UserLogin data from dynamoDb") *>
          scanamo
            .exec(for {
              scan <- table.scan()
              res = scan.sequence match {
                      case Left(err)      => Left[GameException, List[UserLogin]](DynamoReaderException(err.toString))
                      case Right(records) =>
                        Right[GameException, List[UserLogin]](records.mapFilter(record => toUserLogin(record.json).toOption))
                    }
            } yield res)
            .pure

    }

  private def toUserLogin(jsonString: String): ErrorOr[UserLogin] =
    parser.parse(jsonString) match {
      case Left(parsingFailure) => Left[GameException, UserLogin](JsonParsingFailure(parsingFailure.getMessage()))
      case Right(json)          =>
        json.as[UserLogin] match {
          case Left(decodingFailure) => Left[GameException, UserLogin](JsonDecodingException(decodingFailure.getMessage()))
          case Right(userLogin)      => Right[GameException, UserLogin](userLogin)
        }
    }

}
