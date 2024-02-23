package http.login

import http.BaseEndpoint.{baseEndpoint, baseEndpointNotSecured}
import http.GameExceptionResponse
import http.login.domain.LoginRequest
import http.security.SecuredEndpoints.{AppEndpoint, AppEndpointSecretWithError, AppEndpointWithError}
import http.security.errors.Failure
import http.state.domain._
import sttp.tapir.RawBodyType.StringBody
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object LoginEndpoints {

  lazy val endpoints: List[AppEndpoint[_, _, _]] = List(
    login
  )

  lazy val login: AppEndpoint[LoginRequest, Failure[GameExceptionResponse], Boolean] = baseEndpointNotSecured
    .in("login")
    .post
    .summary("Authenticate user credentials")
    .description("Authenticate user credentials")
    .tag("Login")
    .in(jsonBody[LoginRequest])
    .out(jsonBody[Boolean])

}
