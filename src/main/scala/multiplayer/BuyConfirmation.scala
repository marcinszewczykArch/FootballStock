package multiplayer

import services.domain.PlayerSimple

case class BuyConfirmation (
                             playerBoughtId: Int, //todo: to playerId
                             sharesBought: Double,
                             price: BigDecimal,
                             newUserState: UserGameState
                           )
