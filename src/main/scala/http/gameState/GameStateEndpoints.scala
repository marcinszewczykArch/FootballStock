package http.gameState

import game.gameState.User
import http.gameState.domain.UserGameStateResponse
import http.security.SecuredEndpoints.secretBearer
import http.security.SecuredEndpoints.AppEndpointSecret
import http.security.errors.authorisationErrors
import http.security.errors.Failure
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object GameStateEndpoints {

  lazy val endpoints: List[Endpoint[_, _, _, _, _]] = List(
    getUserGameState
  )
  val ApiVersion = "v1"


  lazy val getUserGameState: AppEndpointSecret[User, Failure[Unit], UserGameStateResponse] = baseEndpoint
    .get
    .in("state")
    .in(query[User]("user"))
    .summary("Get users game state")
    .description("Scan DB to find current user game state")
    .tag("GameState")
    .out(jsonBody[UserGameStateResponse])
    .errorOut(authorisationErrors)

  private val baseEndpoint: AppEndpointSecret[Unit, Unit, Unit] = endpoint
    .in(ApiVersion)
    .securityIn(secretBearer)

}
