package http.login

import http.BaseEndpoint.baseEndpointNotSecured
import http.GameExceptionResponse
import http.login.domain.{CreateUserRequest, LoginRequest}
import http.security.SecuredEndpoints.AppEndpoint
import http.security.errors.Failure
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

object LoginEndpoints {

  lazy val endpoints: List[AppEndpoint[_, _, _]] = List(
    login,
    newUser
  )

  lazy val login: AppEndpoint[LoginRequest, Failure[GameExceptionResponse], String] = baseEndpointNotSecured
    .in("login")
    .post
    .summary("Authenticate user credentials")
    .description("Authenticate user credentials")
    .tag("Login")
    .in(jsonBody[LoginRequest])
    .out(jsonBody[String])

  lazy val newUser: AppEndpoint[CreateUserRequest, Failure[GameExceptionResponse], String] = baseEndpointNotSecured
    .in("newUser")
    .post
    .summary("Create new game user")
    .description("Create new game user with given unique username")
    .tag("Login")
    .in(jsonBody[CreateUserRequest])
    .out(jsonBody[String])

}
