//import cats.effect._
//import cats.effect.testing.scalatest.AsyncIOSpec
//import com.dimafeng.testcontainers.{Container, ForAllTestContainer, MockServerContainer}
//import config.AppConfig
//import config.AppConfig.AwsConfig
//import http.security.{EloTokenVerification, TokenVerification}
//import org.http4s.blaze.client.BlazeClientBuilder
//import org.mockserver.client.MockServerClient
//import org.scalatest.matchers.should.Matchers
//import org.scalatest.wordspec.AsyncWordSpec
//import org.scalatest.{BeforeAndAfterEach, EitherValues}
//import org.typelevel.log4cats.slf4j.Slf4jFactory
//import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
//import sttp.client3.http4s.Http4sBackend
//import sttp.client3.{asStringAlways, basicRequest}
//import sttp.model.{StatusCode, Uri}
//import testUtils.MockServerContainerUtils
//import utils.TimeProvider
//
//import scala.concurrent.ExecutionContext
//
//class httpIT extends AsyncWordSpec with AsyncIOSpec with ForAllTestContainer with BeforeAndAfterEach with Matchers with EitherValues {
//
//  private implicit val testLoggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
//  override implicit def executionContext: ExecutionContext  = ExecutionContext.global
//  implicit val log: SelfAwareStructuredLogger[IO]           = LoggerFactory.getLoggerFromName[IO](classOf[httpIT].getName)
//  lazy val mockServerClient: MockServerClient               = MockServerContainerUtils.createClient(mockServerContainer)
//  val mockServerContainer: MockServerContainer              = MockServerContainerUtils.createContainer()
//  implicit val timeProvider: TimeProvider[IO]               = TimeProvider.impl[IO]
//
//  "FootballStock" when {
//    "simple request is passed" when {
//      "all is set up" should {
//        "handle request and return list of user states" in runtimeEnvironment
//          .use { case (serverAddress, backend) =>
//            for {
//              response <- backend.send(
//                            basicRequest
//                              .get(serverAddress.withPath("v1", "state", "all"))
//                              .header("Authorization", "Bearer elo")
//                              .response(asStringAlways)
//                          )
//            } yield response.code shouldBe StatusCode.Ok
//          }
//          .unsafeToFuture()
//      }
//    }
//  }
//
//  def runtimeEnvironment(implicit timeProvider: TimeProvider[IO]) = {
//    implicit val tokenVerification: TokenVerification[IO] = EloTokenVerification
//    val awsConfigOverride                                 = (aws: AwsConfig) =>
//      AwsConfig(
//        accessKey = aws.accessKey,
//        secretKey = aws.secretKey,
//        region = aws.region,
//        endpointOverride = "http://0.0.0.0:4000/" //todo: this will use separate container for DynamoDb
//      )
//
//    for {
//      rawAppConfig   <- Resource.eval(AppConfig.getTypesafeConfig[IO])
//      appConfig      <- Resource.eval(AppConfig.parseAppConfig[IO](rawAppConfig))
//      dynamoDbClient <- Resource.eval(FootballStockApp.buildDynamoDbClient(awsConfigOverride(appConfig.aws)))
//      (gameEngine, _) = FootballStockApp.getGameElements(appConfig, dynamoDbClient)
//      server <- FootballStockApp.httpServerResource(appConfig, gameEngine)
//      client <- BlazeClientBuilder[IO].resource.map(Http4sBackend.usingClient(_))
//    } yield (Uri(server.address.getHostName, server.address.getPort), client)
//
//  }
//
//  def container: Container = mockServerContainer
//
//  override protected def afterEach(): Unit = {
//    super.afterEach()
//    val _ = mockServerClient.reset()
//  } //todo: remove all from DynamoDb
//}
