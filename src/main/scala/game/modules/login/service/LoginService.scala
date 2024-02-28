package game.modules.login.service

import cats.data.EitherT
import cats.effect._
import cats.implicits.{toBifunctorOps, toFunctorOps}
import config.AppConfig.LoginConfig
import game.GameException
import game.GameException.IncorrectLoginOrPasswordException
import game.modules.login.domain._
import game.modules.login.memory.{LoginMemory, TokenMemory}
import game.modules.state.domain.User
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.TimeProvider
import utils.Type.ErrorOr

import java.time.Duration
import java.util.UUID

trait LoginService[F[_]] {

  def addUserLogin(userForm: UserForm): F[Unit]
  def getUserData(user: User): F[ErrorOr[UserData]]
  def getAllUserData(): F[ErrorOr[List[UserData]]]
  def login(user: User)(password: String): F[ErrorOr[UserLoginResponse]]

  def validateTokenFor(user: User)(token: String): F[ErrorOr[Boolean]]

}

object LoginService {
  val AdminRole            = "ADMIN_ROLE"
  val UserRole             = "USER_ROLE"
  private val hashProvider = HashProvider.bcrypt

  def impl[F[_]: Sync: LoggerFactory](
    loginMemory: LoginMemory[F],
    tokenMemory: TokenMemory[F],
    loginConfig: LoginConfig
  )(
    implicit timeProvider: TimeProvider[F]
  ) = new LoginService[F] {

    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[LoginService[F]].getName)

    override def addUserLogin(userForm: UserForm): F[Unit] =
      //todo: validate user not exists first!!!
      loginMemory.addUserLogin(toUserLogin(userForm))

    //todo: add userState from UserStateService

    override def getUserData(
      user: User
    ): F[ErrorOr[UserData]] = loginMemory.getUserLogin(user).map(_.map(toUserData))

    override def getAllUserData(
    ): F[ErrorOr[List[UserData]]] = loginMemory.getAllUserLogins().map(_.map(_.map(toUserData)))

    import scala.jdk.DurationConverters._

    val javaDuration: Duration = java.time.Duration.ofNanos(123456)
    javaDuration.toScala

    override def login(
      user: User
    )(
      password: String
    ): F[ErrorOr[UserLoginResponse]] = (for {
      userLogin <- EitherT.apply(loginMemory.getUserLogin(user))
      now       = timeProvider.getCurrentTimestamp
      isSuccess = hashProvider.hashVerify(password)(userLogin.hash)
      tokenData <- isSuccess match {
                     case false => EitherT.leftT[F, TokenData](IncorrectLoginOrPasswordException()).leftWiden[GameException]
                     case true  =>
                       EitherT.rightT[F, GameException](
                         TokenData(
                           user = user,
                           token = UUID.randomUUID().toString,
                           start = now,
                           end = now.plus(loginConfig.tokenExpiryTime.length, loginConfig.tokenExpiryTime.unit.toChronoUnit),
                           roles = Nil
                         )
                       )
                   }
      _         <- EitherT.liftF[F, GameException, Unit](tokenMemory.addToken(tokenData))
    } yield UserLoginResponse(
      tokenData.user,
      tokenData.token,
      tokenData.start,
      tokenData.end,
      tokenData.roles
    )).value

    override def validateTokenFor(
      user: User
    )(
      token: String
    ): F[ErrorOr[Boolean]] = (for {
      tokenData <- EitherT(tokenMemory.findTokenData(token, user))
      now               = timeProvider.getCurrentTimestamp
      isExpired         = tokenData.end.isBefore(now)
      isForCorrectUser  = tokenData.user == user //todo: this is obvious from dynamoDB query
      isForCorrectRoles = true //todo: roles to be implemented
      isValid           = !isExpired && isForCorrectUser && isForCorrectRoles
    } yield isValid).value

  }

  private def toUserData(userLogin: UserLogin) =
    UserData(
      user = userLogin.user,
      email = userLogin.email,
      role = userLogin.role
    )

  private def toUserLogin(userForm: UserForm) =
    UserLogin(
      user = User(userForm.user),
      hash = hashProvider.passwordToHash(userForm.password),
      email = userForm.email,
      role = UserRole
    )

}
