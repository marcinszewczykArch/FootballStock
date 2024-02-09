package http.gameState

import game.errors.GameException
import http.BaseEndpoint.baseEndpoint
import http.gameState.domain._
import http.security.SecuredEndpoints.AppEndpointSecret
import http.security.SecuredEndpoints.AppEndpointSecretWithError
import http.security.SecuredEndpoints.secretBearer
import http.security.errors
import http.security.errors.BusinessFailure
import http.security.errors.Failure
import http.security.errors.authorisationErrors
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object GameStateEndpoints {

  lazy val endpoints: List[AppEndpointSecretWithError[_, _]] = List(
    getUserGameState,
    getAllGameState,
    buyPlayer,
    sellPlayer,
    createNewUser
  )

  lazy val getUserGameState: AppEndpointSecretWithError[String, UserGameStateResponse] = baseEndpoint
    .get
    .in("state")
    .in(query[String]("user"))
    .summary("Get users game state")
    .description("Scan DB to find current user game state")
    .tag("GameState")
    .out(jsonBody[UserGameStateResponse])

  lazy val getAllGameState: AppEndpointSecretWithError[_, List[UserGameStateResponse]] = baseEndpoint
    .get
    .in("state"/"all")
    .summary("Get all users game state")
    .description("Scan DB to fetch all users game state")
    .tag("GameState")
    .out(jsonBody[List[UserGameStateResponse]])

  lazy val buyPlayer: AppEndpointSecretWithError[BuyPlayerRequest, BuyPlayerResponse] = baseEndpoint
    .in("buy")
    .post
    .summary("Proceed with buy player stock transaction.")
    .description("You can have no more than 100 stocks of the same players.")
    .tag("GameState")
    .in(jsonBody[BuyPlayerRequest])
    .out(jsonBody[BuyPlayerResponse])

  lazy val sellPlayer: AppEndpointSecretWithError[SellPlayerRequest, SellPlayerResponse] = baseEndpoint
    .in("sell")
    .post
    .summary("Proceed with sell player stock transaction.")
    .description("You can have no more than 100 stocks of the same players.")
    .tag("GameState")
    .in(jsonBody[SellPlayerRequest])
    .out(jsonBody[SellPlayerResponse])

  lazy val createNewUser: AppEndpointSecretWithError[String, CreateNewUserResponse] = baseEndpoint
    .in("newUser")
    .in(query[String]("user"))
    .post
    .summary("Create new game user.")
    .description("User name has to be unique, case insensitive.")
    .tag("GameState")
    .out(jsonBody[CreateNewUserResponse])

}
