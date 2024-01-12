package http.security

import cats.Monad
import cats.implicits.toFunctorOps
import http.security.SecuredEndpoints.{BearerToken, ServerAppEndpoint}
import sttp.tapir.EndpointInput.AuthType
import sttp.tapir.TapirAuth.bearer
import sttp.tapir.server.{PartialServerEndpoint, ServerEndpoint}
import sttp.tapir.{Endpoint, EndpointInput, PublicEndpoint}
import errors.Failure
import org.typelevel.log4cats.LoggerFactory


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
    private val method: String = endpoint.method.map(_.method).getOrElse("")
    private val transactionName = s"$method ${endpoint.showPathTemplate(showQueryParam = None, includeAuth = false)}"

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

  type BearerToken = Secret[String]
  type AppEndpointSecret[INPUT, ERROR_OUTPUT, OUTPUT] = Endpoint[BearerToken, INPUT, ERROR_OUTPUT, OUTPUT, Any]
  type AppEndpoint[INPUT, ERROR_OUTPUT, OUTPUT] = PublicEndpoint[INPUT, ERROR_OUTPUT, OUTPUT, Any]
  type ServerAppEndpoint[F[_]] = ServerEndpoint[Any, F]

  val secretBearer: EndpointInput.Auth[BearerToken, AuthType.Http] =
    bearer[String]().map(Secret(_))(_.value)

}
