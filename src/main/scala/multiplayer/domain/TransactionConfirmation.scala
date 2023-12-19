package multiplayer.domain

case class TransactionConfirmation(
  transactionType: TransactionType,
  playerId: Int, //todo: to PlayerId
  shares: Double,
  value: BigDecimal,
  newUserState: UserGameState
)
