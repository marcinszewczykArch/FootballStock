package http

import http.security.SecuredEndpoints.{AppEndpointSecretWithError, secretBearer}
import http.security.{CirceExtraConfiguration, errors}
import http.security.errors.BusinessFailure
import io.circe.Codec
import io.circe.generic.extras.semiauto.deriveConfiguredCodec
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe.jsonBody
import sttp.tapir._

object BaseEndpoint {

  val ApiVersion = "v1"

  val baseEndpoint: AppEndpointSecretWithError[Unit, Unit] = endpoint
    .in(ApiVersion)
    .securityIn(secretBearer)
    .errorOut(
      errors.errors[GameExceptionResponse](
        oneOfVariantValueMatcher(
          StatusCode.PreconditionFailed,
          jsonBody[BusinessFailure[GameExceptionResponse]]
            .description("???")
          //            .example(allocateAlternativeInventoryErrorExample) //todo: add example
        ) { case BusinessFailure(_) => true }
      )
    )

}

case class GameExceptionResponse(message: String) extends Throwable with Product with Serializable

object GameExceptionResponse extends CirceExtraConfiguration {
  implicit val gameExceptionResponseCodec: Codec[GameExceptionResponse] = deriveConfiguredCodec[GameExceptionResponse]
}
