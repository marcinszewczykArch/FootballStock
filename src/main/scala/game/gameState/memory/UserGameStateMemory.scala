package game.gameState.memory

import cats.Applicative
import cats.effect._
import cats.implicits.catsSyntaxApplicativeId
import cats.implicits.catsSyntaxApplyOps
import cats.syntax.all._
import game.errors.GameException
import game.errors.GameException.DynamoDbUpdateException
import game.errors.GameException.DynamoReaderException
import game.errors.GameException.JsonDecodingException
import game.errors.GameException.JsonParsingFailure
import game.gameState.domain.User
import game.gameState.domain.UserGameState
import io.circe.parser
import io.circe.syntax.EncoderOps
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger

import java.time.Instant

trait UserGameStateMemory[F[_]] {

  def getByUser(user: User): F[Either[GameException, UserGameState]]
  def getAll(): F[Map[User, UserGameState]]
  def update(user: User)(newUserState: UserGameState)(versionNumber: Instant): F[Either[GameException, Unit]]
  def save(user: User)(initialUserState: UserGameState): F[Either[GameException, Unit]]

}

object UserGameStateMemory {

  def impl[F[_]: Sync: LoggerFactory](scanamo: Scanamo): UserGameStateMemory[F] = new UserGameStateMemory[F] {

    import org.scanamo._
    import org.scanamo.generic.auto._
    import org.scanamo.syntax._

    implicit val log: SelfAwareStructuredLogger[F] =
      LoggerFactory.getLoggerFromName[F](classOf[UserGameStateMemory[F]].getName)

    private val table = Table[UserGameStateTable]("UserGameState")
    private case class UserGameStateTable(user: String, json: String, updatedAt: String)

    override def getByUser(user: User): F[Either[GameException, UserGameState]] =
      log.debug(s"getting user state for $user from dynamoDb") *> (scanamo
        .exec(table.get("user" === user.value))
        .map(_.left.map(err => DynamoReaderException(err.toString))) match {
        case Some(value) =>
          value
            .map(_.json)
            .flatMap(toUserState)
            .pure

        case None =>
          Applicative[F].pure(Left[GameException, UserGameState](DynamoReaderException(s"Result for $user not found in memory.")))
      })

    private def toUserState(jsonString: String): Either[GameException, UserGameState] =
      parser.parse(jsonString) match {
        case Left(parsingFailure) => Left[GameException, UserGameState](JsonParsingFailure(parsingFailure.getMessage()))
        case Right(json)          =>
          json.as[UserGameState] match {
            case Left(decodingFailure) => Left[GameException, UserGameState](JsonDecodingException(decodingFailure))
            case Right(userGameState)  => Right[GameException, UserGameState](userGameState)
          }
      }

    override def getAll(): F[Map[User, UserGameState]] =
      log.debug(s"getting all user states from dynamoDb") *> scanamo
        .exec(table.scan())
        .sequence
        .getOrElse(Nil)
        .mapFilter { record =>
          val user          = User(record.user)
          val userGameState = toUserState(record.json).toOption
          userGameState.map(user -> _)
        }
        .toMap
        .pure

    override def save(user: User)(newUserState: UserGameState): F[Either[GameException, Unit]] =
      log.debug(s"saving new user game state for $user to dynamoDb") *> scanamo
        .exec(
          table.put(
            UserGameStateTable(
              user = user.value,
              json = newUserState.asJson.toString(),
              updatedAt = newUserState.updatedAt.toString
            )
          )
        )
        .asRight[GameException]
        .pure

    override def update(user: User)(newUserState: UserGameState)(versionNumber: Instant): F[Either[GameException, Unit]] =
      log.debug(s"updating user game state for $user to dynamoDb") *>
        scanamo
          .exec(
            table
              .when("updatedAt" === versionNumber.toString) //condition to verify optimistic locking
              .put(
                UserGameStateTable(
                  user = user.value,
                  json = newUserState.asJson.toString(),
                  updatedAt = newUserState.updatedAt.toString
                )
              )
          )
          .left
          .map(err => DynamoDbUpdateException(" [optimistic locking exception for this version number] " + err.toString))
          .leftWiden[GameException]
          .pure

  }

}
