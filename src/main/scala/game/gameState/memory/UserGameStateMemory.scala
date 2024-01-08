package game.gameState.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.{catsSyntaxApplicativeId, toFunctorOps}
import game.errors.GameException
import game.errors.GameException.{DynamoReaderException, JsonParsingFailure, PlayerJsonDecodingException, UserNotFoundException}
import game.gameState.{User, UserGameState}
import io.circe.{Json, parser}
import org.scanamo.Scanamo
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.{catsSyntaxApplyOps, toFlatMapOps, toFunctorOps}
import cats.syntax.all._
import io.circe.syntax.EncoderOps

trait UserGameStateMemory[F[_]] {

  def getByUser(user: User): F[Either[GameException, UserGameState]] //todo: User(value: String) extends AnyVal
  def getAll(): F[Map[User, UserGameState]]
  def save(user: User)(newUserState: UserGameState): F[Either[GameException, Unit]]

}

object UserGameStateMemory {

//  def impl[F[_]](
//    ref: Ref[F, Map[User, UserGameState]]
//  )(
//    implicit F: Sync[F]
//  ): UserGameStateMemory[F] =
//    new UserGameStateMemory[F] {
//
//      def save(user: User)(newUserState: UserGameState): F[Either[GameException, Unit]] = (for {
//        _ <- EitherT.right[GameException](ref.update(_ + (user -> newUserState)))
//      } yield ()).value
//
//      override def getByUser(user: User): F[Either[GameException, UserGameState]] = ref
//        .get
//        .map(_.get(user) match {
//          case Some(userStats) => Right(userStats)
//          case None            => Left(UserNotFoundException(user))
//        })
//
//      override def getAll(): F[Map[User, UserGameState]]  = ref.get
//
//    }

  def implDynamoDb[F[_]: Sync: LoggerFactory](scanamo: Scanamo): UserGameStateMemory[F] = new UserGameStateMemory[F] {

    import org.scanamo._
    import org.scanamo.generic.auto._
    import org.scanamo.syntax._

    implicit val log: SelfAwareStructuredLogger[F] =
      LoggerFactory.getLoggerFromName[F](classOf[UserGameStateMemory[F]].getName)

    private val table = Table[UserGameStateTable]("UserGameState")
    private case class UserGameStateTable(user: String, json: String)

    override def getByUser(user: User): F[Either[GameException, UserGameState]] =
      log.debug(s"getting user state for $user from dynamoDb") *> (scanamo
      .exec(table.get("user" === user.value))
      .map(_.left.map(err => DynamoReaderException(err.toString))) match {
      case Some(value) =>
        value
          .map(_.json)
          .flatMap(toUserState)
          .pure

      case None => Applicative[F].pure(Left[GameException, UserGameState](DynamoReaderException(s"Result for $user not found in memory.")))
    })

    private def toUserState(jsonString: String): Either[GameException, UserGameState] =
      parser.parse(jsonString) match {
        case Left(parsingFailure) => Left[GameException, UserGameState](JsonParsingFailure(parsingFailure.getMessage()))
        case Right(json)          =>
          json.as[UserGameState] match {
            case Left(decodingFailure) => Left[GameException, UserGameState](PlayerJsonDecodingException(decodingFailure))
            case Right(userGameState)  => Right[GameException, UserGameState](userGameState)
          }
      }

    override def getAll(): F[Map[User, UserGameState]] =
      log.debug(s"getting all user states from dynamoDb") *> scanamo
        .exec(table.scan())
        .sequence
        .getOrElse(Nil)
        .mapFilter { record =>
          val user = User(record.user)
          val userGameState = toUserState(record.json).toOption
          userGameState.map(user -> _)
        }
        .toMap
        .pure

    override def save(user: User)(newUserState: UserGameState): F[Either[GameException, Unit]] =
      log.debug(s"saving updated user game state for $user to dynamoDb") *> scanamo
        .exec(
          table.put(
            UserGameStateTable(
              user = user.value,
              json = newUserState.asJson.toString()
            )
          )
        )
        .asRight[GameException]
        .pure

  }

}
