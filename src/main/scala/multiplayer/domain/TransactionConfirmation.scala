package multiplayer.domain

import services.domain.PlayerId

case class TransactionConfirmation(
  transactionType: TransactionType,
  playerId: PlayerId, //todo: to PlayerId
  shares: Int,
  value: BigDecimal,
  newUserState: UserGameState
)
