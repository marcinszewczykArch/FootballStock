package utils

import java.time.Instant
import scala.util.Try

object Parser {

  // 2023-12-19T11:30:36.754874 <- string format
  // 2007-12-03T10:15:30.00Z.   <- required format
  def toInstantOrFarPastForUpdateAt(updatedAt: Option[String]): Instant =
    updatedAt
      .map(_.take(20).concat("00Z"))
      .map(Instant.parse)
      .getOrElse(Instant.MIN)

  // "Mar 17, 2010" <- string format
  // "MMM dd, yyyy" <- pattern
  def toInstantOrFarPastForDate(date: Option[String]): Instant = {
    val format = new java.text.SimpleDateFormat("MMM dd, yyyy")
    date
      .map(format.parse(_).toInstant)
      .getOrElse(Instant.MIN)
  }

  def toBigDecimalOrZero(value: Option[String]): BigDecimal = Try {
    val str           = value.get
    val strWithNoEuro = str.drop(1)
    strWithNoEuro.toList match {
      case value :+ 'k' => BigDecimal(value.mkString.toDouble * 1_000)
      case value :+ 'm' => BigDecimal(value.mkString.toDouble * 1_000_000)
      case _            => BigDecimal(0)
    }
  }.getOrElse(BigDecimal(0)) //todo: add log

  implicit class CaseClassToString(c: AnyRef) {

    def toStringWithFields: Map[String, Any] = c.getClass.getDeclaredFields.foldLeft(Map[String, Any]()) { (map, field) =>
      field.setAccessible(true)
      map + (field.getName -> field.get(c))
    }

  }

}
