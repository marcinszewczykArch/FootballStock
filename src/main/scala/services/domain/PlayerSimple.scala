package services.domain

final case class PlayerSimple(
  id: Int,
  name: String,
  position: String,
  club: String,
  age: String, //todo: to Int
  nationality: String,
  marketValue: BigDecimal //todo: to Big Decimal with currency
)

object PlayerSimple {

}
