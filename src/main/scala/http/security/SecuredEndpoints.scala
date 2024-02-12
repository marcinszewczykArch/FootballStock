package http.security

import cats.Monad
import cats.implicits.{toBifunctorOps, toFunctorOps}
import game.GameException
import http.GameExceptionResponse
import http.security.SecuredEndpoints.{BearerToken, ServerAppEndpoint}
import http.security.errors.{BusinessFailure, Failure}
import org.typelevel.log4cats.LoggerFactory
import sttp.tapir.EndpointInput.AuthType
import sttp.tapir.{Endpoint, EndpointInput, PublicEndpoint}
import sttp.tapir.TapirAuth.bearer
import sttp.tapir.server.{PartialServerEndpoint, ServerEndpoint}

abstract class SecuredEndpoints[F[_]: LoggerFactory: Monad](
  tokenIntrospection: TokenVerification[F]
) {

  implicit class EndpointOpsWithPandaSecurity[I, E, O](endpoint: Endpoint[BearerToken, I, Failure[E], O, Any]) {

    def endpointSecured(role: RoleSelection): PartialServerEndpoint[BearerToken, Unit, I, Failure[E], O, Any, F] =
      endpoint.serverSecurityLogic[Unit, F] { token =>
        tokenIntrospection.verify(role, token)
      }

  }

  implicit class PartialServerEndpointOpsWithContextualServerLogic[I, E, O](
    endpoint: PartialServerEndpoint[BearerToken, Unit, I, Failure[E], O, Any, F]
  ) {
    private val method: String  = endpoint.method.map(_.method).getOrElse("")
    private val transactionName = s"$method ${endpoint.showPathTemplate(showQueryParam = None, includeAuth = false)}"

    def withServerLogic(logic: I => F[Either[E, O]]): ServerAppEndpoint[F] =
      serverLogicInternal(input => logic(input).map(_.leftMap[Failure[E]](BusinessFailure(_))))

    def withServerLogicSuccess(logic: I => F[O]): ServerAppEndpoint[F] =
      serverLogicInternal(input => logic(input).map(Right(_)))

    private def serverLogicInternal(logic: I => F[Either[Failure[E], O]]): ServerAppEndpoint[F] =
      endpoint
        .serverLogic { _ => input =>
          logic(input)
        }

  }

}

object SecuredEndpoints {

  type BearerToken                                    = Secret[String]
  type AppEndpointSecret[INPUT, ERROR_OUTPUT, OUTPUT] = Endpoint[BearerToken, INPUT, ERROR_OUTPUT, OUTPUT, Any]
  type AppEndpointSecretWithError[INPUT, OUTPUT]      = Endpoint[BearerToken, INPUT, Failure[GameExceptionResponse], OUTPUT, Any]
  type AppEndpoint[INPUT, ERROR_OUTPUT, OUTPUT]       = PublicEndpoint[INPUT, ERROR_OUTPUT, OUTPUT, Any]
  type ServerAppEndpoint[F[_]]                        = ServerEndpoint[Any, F]

  val secretBearer: EndpointInput.Auth[BearerToken, AuthType.Http] =
    bearer[String]().map(Secret(_))(_.value)

}
