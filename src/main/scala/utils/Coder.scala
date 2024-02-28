package utils

import game.modules.login.domain.TokenData
import game.modules.state.domain.User
import io.circe.parser

import java.nio.charset.StandardCharsets
import java.util.Base64

object Coder {

  def encodeString(inputString: String): String = {
    val encodedBytes: Array[Byte] = inputString.getBytes(StandardCharsets.UTF_8)
    val encodedString: String = Base64.getEncoder.encodeToString(encodedBytes)
    encodedString
  }

  def decodeString(encodedString: String): String = {
    val decodedBytes: Array[Byte] = Base64.getDecoder.decode(encodedString)
    val decodedString = new String(decodedBytes, StandardCharsets.UTF_8)
    decodedString
  }

  val getUserFromToken: String => Option[User] = (header: String) => {
    val encodedToken = header.split(" ").toList.last
    val decodedToken = Coder.decodeString(encodedToken)
    parser.parse(decodedToken).flatMap(_.as[TokenData]).map(_.user).toOption
  }

}
