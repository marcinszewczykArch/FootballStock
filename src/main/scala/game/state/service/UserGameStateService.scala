package game.state.service

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.toFunctorOps
import game.GameException
import GameException.{SharesNumberException, UserAlreadyExistsException}
import game.event.Event.SYSTEM_USER_NAME
import game.state.domain.{Shares, User, UserGameState}
import game.state.memory.UserGameStateMemory
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.TimeProvider

import java.time.Instant

trait UserGameStateService[F[_]] {

  def getStateForUser(user: User): F[Either[GameException, UserGameState]]
  def getAllGameStates(): F[Either[GameException, Map[User, UserGameState]]]
  def updateGameStateFroUser(user: User)(newUserState: UserGameState)(versionNumber: Instant): F[Either[GameException, Unit]]
  def saveGameStateFroUser(user: User)(initialUserState: UserGameState): F[Either[GameException, Unit]]

  def validateUserNotExists(user: User): F[Either[GameException, Unit]]

  def calculateSharesAfterSell(
    sharesInPortfolio: Option[List[Shares]],
    sharesToSell: Int
  ): F[Either[GameException, List[Shares]]]

  def calculateSharesAfterBuy(
    sharesInPortfolio: Option[List[Shares]],
    sharesToBuy: Int,
    currentPlayerMarketValue: BigDecimal
  )(
    implicit timeProvider: TimeProvider[F]
  ): F[Either[GameException, List[Shares]]]

}

object UserGameStateService {

  def impl[F[_]: Sync: LoggerFactory](userGameStateMemory: UserGameStateMemory[F]) = new UserGameStateService[F] {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[UserGameStateService[F]].getName)

    override def getStateForUser(
      user: User
    ): F[Either[GameException, UserGameState]] = userGameStateMemory.getByUser(user)

    override def getAllGameStates(): F[Either[GameException, Map[User, UserGameState]]] = userGameStateMemory.getAll()

    override def updateGameStateFroUser(
      user: User
    )(
      newUserState: UserGameState
    )(
      versionNumber: Instant
    ): F[Either[GameException, Unit]] = userGameStateMemory.update(user)(newUserState)(versionNumber)

    override def saveGameStateFroUser(
      user: User
    )(
      initialUserState: UserGameState
    ): F[Either[GameException, Unit]] = userGameStateMemory.save(user)(initialUserState)

    override def validateUserNotExists(user: User): F[Either[GameException, Unit]] = (for {
      allUsers <- EitherT(getAllGameStates().map(_.map(_.keySet + User(SYSTEM_USER_NAME))))
      _        <- EitherT.fromEither(allUsers.contains(user) match {
                    case true  => Left[GameException, Unit](UserAlreadyExistsException(user))
                    case false => Right[GameException, Unit](())
                  })
    } yield ()).value

    override def calculateSharesAfterSell(
      sharesInPortfolio: Option[List[Shares]],
      sharesToSell: Int
    ): F[Either[GameException, List[Shares]]] = Applicative[F].pure {
      sharesInPortfolio.sum - sharesToSell >= 0 match {
        case true  => Right(sharesInPortfolio |-| sharesToSell)
        case false => Left(SharesNumberException(sharesInPortfolio.sum - sharesToSell))
      }
    }

    override def calculateSharesAfterBuy(
      sharesInPortfolio: Option[List[Shares]],
      sharesToBuy: Int,
      currentPlayerMarketValue: BigDecimal
    )(
      implicit timeProvider: TimeProvider[F]
    ): F[Either[GameException, List[Shares]]] = Applicative[F].pure {
      sharesInPortfolio.sum + sharesToBuy <= 100 match {
        case true  => Right(sharesInPortfolio |+| Shares(sharesToBuy, currentPlayerMarketValue, timeProvider.getCurrentTimestamp))
        case false => Left(SharesNumberException(sharesInPortfolio.sum + sharesToBuy))
      }
    }

  }

}
