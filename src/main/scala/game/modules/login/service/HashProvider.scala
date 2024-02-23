package game.modules.login.service

trait HashProvider {

  def passwordToHash(password: String): String
  def hashVerify(password: String)(hashFromMemory: String): Boolean

}

object HashProvider {

  def bcrypt = new HashProvider {
    import com.github.t3hnar.bcrypt._

    def passwordToHash(password: String): String = {
      val salt = generateSalt
      password.bcryptBounded(salt)
    }

    def hashVerify(password: String)(hashFromMemory: String): Boolean = password.isBcryptedBounded(hashFromMemory)
  }

}
