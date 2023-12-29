package utils

import game.errors.GameException.ValueParseException

import java.time.Instant
import scala.util.Try

object Parser {

  // 2023-12-19T11:30:36.754874 <- string format
  // 2007-12-03T10:15:30.00Z.   <- required format
  def parseInstant(updatedAt: Option[String]): Instant =
    updatedAt
      .map(_.take(20).concat("00Z"))
      .map(Instant.parse)
      .getOrElse(Instant.MIN)

  def parseMarketValueToBigDecimal(value: Option[String]): Either[ValueParseException, BigDecimal] = Try {
    val str = value.get
    val strWithNoEuro = str.drop(1)
    strWithNoEuro.toList match {
      case value :+ 'k' => BigDecimal(value.mkString.toDouble * 1_000)
      case value :+ 'm' => BigDecimal(value.mkString.toDouble * 1_000_000)
      case _            => BigDecimal(0)
    }
  }.toEither.left.map((err: Throwable) => ValueParseException(value, err))

  implicit class CaseClassToString(c: AnyRef) {

    def toStringWithFields: Map[String, Any] = c.getClass.getDeclaredFields.foldLeft(Map[String, Any]()) { (map, field) =>
      field.setAccessible(true)
      map + (field.getName -> field.get(c))
    }

  }

}
