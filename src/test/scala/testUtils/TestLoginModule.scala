package testUtils

import cats.effect._
import config.AppConfig
import game.GameException
import game.GameException.DynamoReaderException
import game.modules.login.LoginModule
import game.modules.login.domain.{TokenData, UserLogin}
import game.modules.login.memory.{LoginMemory, TokenMemory}
import game.modules.login.service.LoginService
import game.modules.state.domain.User
import org.typelevel.log4cats.LoggerFactory
import utils.TimeProvider
import utils.Type.ErrorOr

object TestLoginModule {

  def impl(
            appConfig: AppConfig,
    loginRef: Ref[IO, Map[User, UserLogin]],
    tokenRef: Ref[IO, List[TokenData]]
  )(
    implicit loggerFactory: LoggerFactory[IO],
    timeProvider: TimeProvider[IO]
  ): LoginModule[IO] = new LoginModule[IO] {

    val loginMemory      = testLoginMemory(loginRef)
    val tokenMemory      = testTokenMemory(tokenRef)
    override val service = LoginService.impl[IO](loginMemory, tokenMemory, appConfig.login)
  }

  private def testLoginMemory(ref: Ref[IO, Map[User, UserLogin]]) = new LoginMemory[IO] {
    override def addUserLogin(login: UserLogin): IO[Unit] = ref.update(_ + (login.user -> login))

    override def getUserLogin(user: User): IO[ErrorOr[UserLogin]] = ref
      .get
      .map(_.get(user) match {
        case Some(userLogin) => Right(userLogin)
        case None            => Left(DynamoReaderException("UserLogin not found in test memory."))
      })

    override def getAllUserLogins(): IO[ErrorOr[List[UserLogin]]] = ref
      .get
      .map(_.values.toList)
      .map(Right[GameException, List[UserLogin]])

  }

  private def testTokenMemory(ref: Ref[IO, List[TokenData]]) = new TokenMemory[IO] {
    override def addToken(token: TokenData): IO[Unit]                             = ref.update(_ :+ token)

    override def findTokenData(token: String, user: User): IO[ErrorOr[TokenData]] = ref
        .get
        .map(_.find(tokenData => tokenData.user == user && tokenData.token == token))
        .map {
          case Some(tokenData) => Right(tokenData)
          case None            => Left(DynamoReaderException(s"Token not found in test memory."))
        }

  }

}
