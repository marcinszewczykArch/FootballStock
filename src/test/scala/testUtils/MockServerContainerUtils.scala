package testUtils

import com.comcast.ip4s.IpLiteralSyntax
import com.dimafeng.testcontainers.MockServerContainer
import config.AppConfig
import config.AppConfig.{AwsConfig, HttpConfig}
import org.mockserver.client.MockServerClient
import software.amazon.awssdk.regions.Region

object MockServerContainerUtils {

  val actualVersion: String = classOf[MockServerClient].getPackage.getImplementationVersion

  def createContainer(): MockServerContainer = {
    val c = MockServerContainer.Def(version = actualVersion).createContainer()
    c
  }

  def createClient(mockServerContainer: MockServerContainer): MockServerClient =
    new MockServerClient(mockServerContainer.host, mockServerContainer.serverPort)

}
