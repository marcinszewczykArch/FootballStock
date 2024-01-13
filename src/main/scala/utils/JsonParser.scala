package utils

import scala.io.Source

object JsonParser {

  def jsonString(source: String): String = {
    val src = Source.fromResource(source)
    val s   = src.mkString
    src.close()
    s
  }

}
