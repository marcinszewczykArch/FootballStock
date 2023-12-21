package multiplayer.domain

import services.domain.PlayerId

case class TransactionConfirmation(
  transactionType: TransactionType,
  playerId: PlayerId, //todo: to PlayerId
  shares: Double,
  value: BigDecimal,
  newUserState: UserGameState
)
