package http.player

import game.errors.GameException
import game.player.service.domain.PlayerProfile
import http.BaseEndpoint.baseEndpoint
import http.gameState.domain._
import http.player.domain.PlayerProfileResponse
import http.player.domain.PlayerSearchResponse
import http.security.SecuredEndpoints.AppEndpointSecretWithError
import http.security.SecuredEndpoints.secretBearer
import http.security.errors
import http.security.errors.BusinessFailure
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object PlayerEndpoints {

  lazy val endpoints: List[AppEndpointSecretWithError[_, _]] = List(
    getPlayer,
    getPlayerSearch
  )

  lazy val getPlayer: AppEndpointSecretWithError[Int, PlayerProfileResponse] = baseEndpoint
    .get
    .in("player")
    .in(query[Int]("playerId"))
    .summary("Get player profile by id")
    .description("Scan DB to find player profile, if not exist make call to Transfermatrk api")
    .tag("Player")
    .out(jsonBody[PlayerProfileResponse])

  lazy val getPlayerSearch: AppEndpointSecretWithError[String, PlayerSearchResponse] = baseEndpoint
    .get
    .in("playerSearch")
    .in(query[String]("playerName"))
    .summary("Search players by given name")
    .description("Request Transfermatrk api to search player by given name")
    .tag("Player")
    .out(jsonBody[PlayerSearchResponse])

}
