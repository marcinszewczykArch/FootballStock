package http.security

import io.circe.generic.extras.Configuration

trait CirceExtraConfiguration {
  implicit val circeConfiguration: Configuration = Configuration.default.withDiscriminator("@type")
}
