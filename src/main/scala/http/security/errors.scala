package http.security

import io.circe.generic.extras.semiauto.{deriveConfiguredCodec, deriveUnwrappedCodec}
import io.circe.{Codec, DecodingFailure, HCursor, JsonObject}
import sttp.model._
import sttp.tapir.EndpointOutput.OneOfVariant
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._

import scala.annotation.nowarn

@nowarn("cat=unused")
object errors extends CirceExtraConfiguration {
  sealed trait Failure[+E] extends Product with Serializable

  final case class Unauthorized(message: String) extends Failure[Nothing]

  object Unauthorized {
    implicit val codec: Codec[Unauthorized] = deriveConfiguredCodec
  }

  final case class Unauthenticated(message: String) extends Failure[Nothing]

  object Unauthenticated {
    implicit val codec: Codec[Unauthenticated] = deriveConfiguredCodec
  }

  final case class BusinessFailure[+E](source: E) extends Failure[E]

  object BusinessFailure {
    implicit def codec[E: Codec]: Codec[BusinessFailure[E]] = deriveUnwrappedCodec
  }

  implicit final val decodeUnit: io.circe.Decoder[Unit] = (c: HCursor) => Left(DecodingFailure("Unit", c.history))

  implicit final val encodeUnit: io.circe.Encoder[Unit] = new io.circe.Encoder.AsObject[Unit] {
    final def encodeObject(a: Unit): JsonObject = JsonObject.empty
  }

  implicit final val codecUnit: io.circe.Codec[Unit] = io.circe.Codec.from[Unit](decodeUnit, encodeUnit)

  def unauthenticated[T]: OneOfVariant[Unauthenticated] =
    oneOfVariantValueMatcher(
      StatusCode.Unauthorized,
      jsonBody[Unauthenticated].example(Unauthenticated("Failed to authenticate"))
    ) { case _: Unauthenticated => true }

  def unauthorized[T]: OneOfVariant[Unauthorized] =
    oneOfVariantValueMatcher(
      StatusCode.Forbidden,
      jsonBody[Unauthorized].example(Unauthorized("Insufficient privileges or invalid scope"))
    ) { case _: Unauthorized => true }

  def authorisationErrors: EndpointOutput.OneOf[Failure[Unit], Failure[Unit]] =
    oneOf(unauthenticated, unauthorized)

  def errors[T](
    businessErrors: OneOfVariant[_ <: Failure[_ <: T]],
    extraVariants: OneOfVariant[_ <: Failure[_ <: T]]*
  ): EndpointOutput.OneOf[Failure[T], Failure[T]] =
    oneOf(unauthenticated[T], unauthorized[T] :: businessErrors :: extraVariants.toList: _*)

}
