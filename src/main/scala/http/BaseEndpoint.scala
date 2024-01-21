package http

import game.errors.GameException
import game.player.service.domain.PlayerProfile
import http.gameState.domain._
import http.player.domain.PlayerProfileResponse
import http.security.SecuredEndpoints.AppEndpointSecretWithError
import http.security.SecuredEndpoints.secretBearer
import http.security.errors
import http.security.errors.BusinessFailure
import sttp.model.StatusCode
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import game.errors.GameException
import http.security.SecuredEndpoints.AppEndpointSecretWithError
import http.security.SecuredEndpoints.secretBearer
import http.security.errors
import http.security.errors.BusinessFailure
import sttp.model.StatusCode
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.endpoint
import sttp.tapir.oneOfVariantValueMatcher

object BaseEndpoint {

  val ApiVersion = "v1"

  val baseEndpoint: AppEndpointSecretWithError[Unit, Unit] = endpoint
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
