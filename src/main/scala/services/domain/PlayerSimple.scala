package services.domain

final case class PlayerSimple(
  id: PlayerId,
  name: String,
  position: String,
  club: String,
  age: String,
  nationality: String,
  marketValue: BigDecimal
)

object PlayerSimple {

}
