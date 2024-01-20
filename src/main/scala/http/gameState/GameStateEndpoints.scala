package http.gameState

import game.errors.GameException
import http.gameState.domain._
import http.security.SecuredEndpoints.{AppEndpointSecret, AppEndpointSecretWithError, secretBearer}
import http.security.errors
import http.security.errors.{BusinessFailure, Failure, authorisationErrors}
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object GameStateEndpoints {

  lazy val endpoints: List[Endpoint[_, _, _, _, _]] = List(
    getUserGameState,
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

  lazy val buyPlayer: AppEndpointSecretWithError[BuyPlayerRequest, BuyPlayerResponse] = baseEndpoint
    .in("buy")
    .post
    .summary("Proceed with buy player stock transaction.")
    .description("You can have no more than 100 stocks of the same players.")
    .tag("BuyStock")
    .in(jsonBody[BuyPlayerRequest])
    .out(jsonBody[BuyPlayerResponse])

  lazy val sellPlayer: AppEndpointSecretWithError[SellPlayerRequest, SellPlayerResponse] = baseEndpoint
    .in("sell")
    .post
    .summary("Proceed with sell player stock transaction.")
    .description("You can have no more than 100 stocks of the same players.")
    .tag("SellStock")
    .in(jsonBody[SellPlayerRequest])
    .out(jsonBody[SellPlayerResponse])

  lazy val createNewUser: AppEndpointSecretWithError[String, CreateNewUserResponse] = baseEndpoint
    .in("newUser")
    .in(query[String]("user"))
    .post
    .summary("Create new game user.")
    .description("User name has to be unique, case insensitive.")
    .tag("CreateNewUser")
    .out(jsonBody[CreateNewUserResponse])

  val ApiVersion = "v1"

  private val baseEndpoint: AppEndpointSecretWithError[Unit, Unit] = endpoint
    .in(ApiVersion)
    .securityIn(secretBearer)
    .errorOut(
      errors.errors[GameException](
        oneOfVariantValueMatcher(
          StatusCode.PreconditionFailed,
          jsonBody[BusinessFailure[GameException]]
            .description("???")
          //            .example(allocateAlternativeInventoryErrorExample) //todo: add example
        ) { case BusinessFailure(_) => true }
      )
    )

}
