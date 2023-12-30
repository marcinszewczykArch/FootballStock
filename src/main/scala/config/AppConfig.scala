package config

import cats.MonadThrow
import cats.effect.IO
import cats.effect.Sync
import cats.implicits.toBifunctorOps
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import config.AppConfig.{HttpConfig, PlayerSearchClientConfig, TransfermarktClientConfig}
import sttp.client3.UriContext
import sttp.model.Uri
import cats.syntax.all._
import pureconfig.ConfigSource
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.error.CannotConvert
import eu.timepit.refined.pureconfig._

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.control.NoStackTrace

final case class AppConfig(
  http: HttpConfig,
  transfermarktClient: TransfermarktClientConfig,
  playerSearchClient: PlayerSearchClientConfig
)

object AppConfig {

  final case class HttpConfig(host: Host, port: Port)

  final case class TransfermarktClientConfig(
    uri: Uri,
    cacheTtl: FiniteDuration,
    failedCacheTtl: FiniteDuration,
    cacheName: String
  )

  final case class PlayerSearchClientConfig(
    uri: Uri,
    cacheTtl: FiniteDuration,
    failedCacheTtl: FiniteDuration,
    cacheName: String
  )

  implicit val hostConfigReader: ConfigReader[Host] = ConfigReader.fromNonEmptyStringOpt(Host.fromString)

  implicit val portConfigReader: ConfigReader[Port] = ConfigReader.fromCursor(_.asInt).emap { int =>
    Port.fromInt(int) match {
      case Some(value) => Right(value)
      case None        => Left(CannotConvert(int.toString, "com.comcast.ip4s.Port", "not a valid port"))
    }
  }

  implicit val uriConfigReader: ConfigReader[Uri] = ConfigReader.fromNonEmptyStringTry(str => Try(uri"$str"))

  def getTypesafeConfig[F[_]: Sync]: F[Config] = Sync[F].blocking(ConfigFactory.load("application.conf"))

  def parseAppConfig[F[_]: MonadThrow](rawConfig: Config): F[AppConfig] = pureconfig
      .ConfigSource
      .fromConfig(rawConfig)
      .load[AppConfig]
      .leftMap(failure => Failure.AppConfigParsingFailure(s"Cannot parse AppConfig: ${failure.prettyPrint()}"))
      .liftTo[F]

  private trait Failure extends NoStackTrace with Product with Serializable { _: RuntimeException => }

  private object Failure {
    final case class AppConfigParsingFailure(message: String) extends RuntimeException(message) with Failure
  }

}
