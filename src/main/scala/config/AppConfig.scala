package config

import cats.MonadThrow
import cats.effect.Sync
import cats.syntax.all._
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import config.AppConfig.{AwsConfig, ClubPlayersClientConfig, ClubProfileClientConfig, ClubSearchClientConfig, HttpConfig, PlayerMarketValueClientConfig, PlayerProfileClientConfig, PlayerSearchClientConfig, PlayerStatsClientConfig, PlayersUpdateCriteriaConfig, UpdaterTaskConfig}
import pureconfig._
import pureconfig.error.CannotConvert
import pureconfig.generic.auto._
import software.amazon.awssdk.regions.Region
import sttp.client3.UriContext
import sttp.model.Uri

import scala.concurrent.duration.FiniteDuration
import scala.util.Try
import scala.util.control.NoStackTrace

final case class AppConfig(
                            http: HttpConfig,
                            aws: AwsConfig,
                            playerProfileClient: PlayerProfileClientConfig,
                            playerSearchClient: PlayerSearchClientConfig,
                            playersUpdateCriteria: PlayersUpdateCriteriaConfig,
                            playerMarketValueClient: PlayerMarketValueClientConfig,
                            playerStatsClient: PlayerStatsClientConfig,
                            clubProfileClient: ClubProfileClientConfig,
                            clubPlayersClient: ClubPlayersClientConfig,
                            clubSearchClient: ClubSearchClientConfig,
                            updaterTask: UpdaterTaskConfig
)

object AppConfig {

  def getTypesafeConfig[F[_]: Sync]: F[Config] = Sync[F].blocking(ConfigFactory.load("application.conf"))

  def parseAppConfig[F[_]: MonadThrow](rawConfig: Config): F[AppConfig] = pureconfig
    .ConfigSource
    .fromConfig(rawConfig)
    .load[AppConfig]
    .leftMap(failure => Failure.AppConfigParsingFailure(s"Cannot parse AppConfig: ${failure.prettyPrint()}"))
    .liftTo[F]

  private trait Failure extends NoStackTrace with Product with Serializable { _: RuntimeException => }

  final case class HttpConfig(host: Host, port: Port)

  final case class AwsConfig(accessKey: String, secretKey: String, region: Region, endpointOverride: String)

  implicit val hostConfigReader: ConfigReader[Host] = ConfigReader.fromNonEmptyStringOpt(Host.fromString)

  implicit val portConfigReader: ConfigReader[Port] = ConfigReader.fromCursor(_.asInt).emap { int =>
    Port.fromInt(int) match {
      case Some(value) => Right(value)
      case None        => Left(CannotConvert(int.toString, "com.comcast.ip4s.Port", "not a valid port"))
    }
  }

  implicit val regionConfigReader: ConfigReader[Region] = ConfigReader.fromNonEmptyStringTry(str => Try(Region.of(str)))

  implicit val uriConfigReader: ConfigReader[Uri] = ConfigReader.fromNonEmptyStringTry(str => Try(uri"$str"))

  final case class PlayerProfileClientConfig(
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

  final case class PlayerMarketValueClientConfig(
    uri: Uri,
    cacheTtl: FiniteDuration,
    failedCacheTtl: FiniteDuration,
    cacheName: String
  )

  final case class PlayerStatsClientConfig(
    uri: Uri,
    cacheTtl: FiniteDuration,
    failedCacheTtl: FiniteDuration,
    cacheName: String
  )

  final case class ClubProfileClientConfig(
    uri: Uri,
    cacheTtl: FiniteDuration,
    failedCacheTtl: FiniteDuration,
    cacheName: String
  )

  final case class ClubPlayersClientConfig(
    uri: Uri,
    cacheTtl: FiniteDuration,
    failedCacheTtl: FiniteDuration,
    cacheName: String
  )

  final case class ClubSearchClientConfig(
    uri: Uri,
    cacheTtl: FiniteDuration,
    failedCacheTtl: FiniteDuration,
    cacheName: String
  )

  final case class UpdaterTaskConfig(
    playersProfileUpdateEvery: FiniteDuration,
    playersValueUpdateEvery: FiniteDuration,
    dividendPayEvery: FiniteDuration,
    dividendYield: Double
  )

  case class PlayersUpdateCriteriaConfig(notUpdatedFor: FiniteDuration)

  private object Failure {
    final case class AppConfigParsingFailure(message: String) extends RuntimeException(message) with Failure
  }

}
