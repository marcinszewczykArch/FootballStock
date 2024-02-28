package http.state

import game.GameException
import http.BaseEndpoint.baseEndpoint
import http.state.domain._
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
    addToWishlist,
    removeFromWishlist
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

  lazy val addToWishlist: AppEndpointSecretWithError[(String, Int), Unit] = baseEndpoint
    .post
    .in("addToWishlist")
    .in(query[String]("user"))
    .in(query[Int]("playerId"))
    .summary("Add player to user wishlist")
    .description("Add player to user wishlist in user game state")
    .tag("GameState")
    .out(jsonBody[Unit])

  lazy val removeFromWishlist: AppEndpointSecretWithError[(String, Int), Unit] = baseEndpoint
    .post
    .in("removeFromWishlist")
    .in(query[String]("user"))
    .in(query[Int]("playerId"))
    .summary("Remove player from user wishlist")
    .description("Remove player from user wishlist in user game state")
    .tag("GameState")
    .out(jsonBody[Unit])

}
