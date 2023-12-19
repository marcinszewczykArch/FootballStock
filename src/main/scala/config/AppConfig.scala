package config

import cats.effect.IO
import cats.effect.Sync
import com.comcast.ip4s.Host
import com.comcast.ip4s.Port
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import config.AppConfig.HttpConfig
import config.AppConfig.TransfermarktClientConfig
import sttp.client3.UriContext
import sttp.model.Uri

final case class AppConfig(
  http: HttpConfig,
  transfermarktClientConfig: TransfermarktClientConfig
)

object AppConfig {

  val default: AppConfig = AppConfig(
    HttpConfig(Host.fromString("localhost").get, Port.fromInt(8080).get),
    TransfermarktClientConfig(uri"https://transfermarkt-api.vercel.app")
  )

  def getTypesafeConfig: IO[Config] = Sync[IO].blocking(ConfigFactory.load("application.conf"))

  def parse(rawConfig: Config): IO[AppConfig] = Sync[IO].blocking {
    (for {
      host                   <- Host.fromString(rawConfig.getString("http.host"))
      port                   <- Port.fromInt(rawConfig.getInt("http.port"))
      transfermarktClientUri <- Uri.parse(rawConfig.getString("transfermarkt-client.uri")).toOption
    } yield AppConfig(HttpConfig(host, port), TransfermarktClientConfig(transfermarktClientUri))).getOrElse(AppConfig.default)
  }

  final case class HttpConfig(host: Host, port: Port)
  final case class TransfermarktClientConfig(uri: Uri)
}
