package game.modules.login.service

import cats.effect._
import cats.implicits.toFunctorOps
import game.modules.login.domain.{UserData, UserForm, UserLogin}
import game.modules.login.memory.LoginMemory
import game.modules.state.domain.User
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import utils.Type.ErrorOr

trait LoginService[F[_]] {

  def addUserLogin(userForm: UserForm): F[Unit]
  def getUserData(user: User): F[ErrorOr[UserData]]
  def getAllUserData(): F[ErrorOr[List[UserData]]]
  def login(user: User)(password: String): F[ErrorOr[Boolean]]

}

object LoginService {
  val AdminRole = "ADMIN_ROLE"
  val UserRole  = "USER_ROLE"
  private val hashProvider = HashProvider.bcrypt

  def impl[F[_]: Sync: LoggerFactory](loginMemory: LoginMemory[F]) = new LoginService[F] {

    override def addUserLogin(userForm: UserForm): F[Unit] = {
      //todo: validate user not exists first!!!
      loginMemory.addUserLogin(toUserLogin(userForm))
      //todo: add userState from UserStateService
    }

    override def getUserData(
      user: User
    ): F[ErrorOr[UserData]] = loginMemory.getUserLogin(user).map(_.map(toUserData))

    override def getAllUserData(
    ): F[ErrorOr[List[UserData]]] = loginMemory.getAllUserLogins().map(_.map(_.map(toUserData)))

    override def login(
      user: User
    )(
      password: String
    ): F[ErrorOr[Boolean]] = {
      loginMemory.getUserLogin(user).map(_.map(_.hash).map(hashFromMemory => hashProvider.hashVerify(password)(hashFromMemory)))
    }

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
