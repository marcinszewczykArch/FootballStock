import FootballStockApp._
import cats.effect._
import cats.implicits._
import config.AppConfig
import config.AppConfig._
import console.ConsolePrinter
import fs2.Stream
import game.{DividendPayer, GameEngine, PlayersUpdater}
import game.modules.club.ClubModule
import game.modules.event.EventModule
import game.modules.player.PlayerModule
import game.modules.state.StateModule
import http.SwaggerRoutes
import http.club.{ClubLogic, ClubRoutes}
import http.event.{EventLogic, EventRoutes}
import http.player.{PlayerProfileLogic, PlayerProfileRoutes}
import http.security.{EloTokenVerification, TokenVerification}
import http.state.{GameStateLogic, GameStateRoutes}
import org.http4s.{BuildInfo, HttpRoutes}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.{Router, Server}
import org.http4s.server.middleware.{CORS, CORSPolicy}
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import utils.TimeProvider

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    (for {
      rawAppConfig   <- Resource.eval(AppConfig.getTypesafeConfig[IO])
      appConfig      <- Resource.eval(AppConfig.parseAppConfig[IO](rawAppConfig))
      dynamoDbClient <- Resource.eval(buildDynamoDbClient(appConfig.aws))
      (gameEngine, playersUpdater, dividendPayer) = getGameElements(appConfig, dynamoDbClient)
      _ <- httpServerResource(appConfig, gameEngine)
      _ <- runBackgroundTasks(appConfig, gameEngine, playersUpdater, dividendPayer)
    } yield ()).useForever

}

object FootballStockApp {
  implicit val timeProvider: TimeProvider[IO]           = TimeProvider.impl[IO]
  implicit val tokenVerification: TokenVerification[IO] = EloTokenVerification
  implicit val loggerFactory: LoggerFactory[IO]         = Slf4jFactory.create[IO]
  private val log                                       = LoggerFactory.getLoggerFromClass(classOf[FootballStockApp.type])

  def httpServerResource(
    appConfig: AppConfig,
    gameEngine: GameEngine[IO]
  )(
    implicit tokenVerification: TokenVerification[IO]
  ): Resource[IO, Server] = for {
    _             <- Resource.eval(log.info(s"starting server with config: $appConfig"))
    swaggerRoutes <- Resource.eval(SwaggerRoutes.routes)
    gameStateLogic     = GameStateLogic.impl[IO](gameEngine)
    playerProfileLogic = PlayerProfileLogic.impl[IO](gameEngine)
    eventLogic         = EventLogic.impl[IO](gameEngine)
    clubLogic          = ClubLogic.impl[IO](gameEngine)
    gameStateRoutes     <- Resource.eval(new GameStateRoutes[IO](tokenVerification).routes(gameStateLogic))
    playerProfileRoutes <- Resource.eval(new PlayerProfileRoutes[IO](tokenVerification).routes(playerProfileLogic))
    eventRoutes         <- Resource.eval(new EventRoutes[IO](tokenVerification).routes(eventLogic))
    clubRoutes          <- Resource.eval(new ClubRoutes[IO](tokenVerification).routes(clubLogic))
    server              <- buildServer(swaggerRoutes <+> gameStateRoutes <+> playerProfileRoutes <+> eventRoutes <+> clubRoutes, appConfig.http)
  } yield server

  private def buildServer(
    routes: HttpRoutes[IO],
    httpConfig: HttpConfig,
    corsService: CORSPolicy = CORS.policy.withAllowOriginAll
  ): Resource[IO, Server] =
    for {
      _      <- Resource.eval(log.info(s"Starting $BuildInfo on ${httpConfig.host}:${httpConfig.port}"))
      server <- EmberServerBuilder
                  .default[IO]
                  .withHost(httpConfig.host)
                  .withPort(httpConfig.port)
                  .withHttpApp(
                    corsService(
                      Router("/" -> routes).orNotFound
                    )
                  )
                  .build
      _      <- Resource.eval(log.info(s"Started $BuildInfo HTTP server"))
      _      <- Resource.eval(IO.println(s"Go to http://localhost:${server.address.getPort}/swagger to open SwaggerUI"))
    } yield server

  def getGameElements(
    appConfig: AppConfig,
    dynamoDbClient: DynamoDbClient
  )(
    implicit timeProvider: TimeProvider[IO]
  ): (GameEngine[IO], PlayersUpdater[IO], DividendPayer[IO]) = {
    val scanamo = Scanamo(dynamoDbClient)

    //modules
    val playerModule = PlayerModule.impl[IO](scanamo, appConfig)
    val clubModule   = ClubModule.impl[IO](scanamo, appConfig)
    val stateModule  = StateModule.impl[IO](scanamo)
    val eventModule  = EventModule.impl[IO](scanamo)

    val gameEngine: GameEngine[IO] = GameEngine.impl(
      stateModule.service,
      eventModule.service,
      playerModule.service,
      clubModule.service
    )

    val playersUpdater: PlayersUpdater[IO] = PlayersUpdater.impl(
      playerModule.service,
      eventModule.service,
      stateModule.service,
      appConfig.playersUpdateCriteria
    )

//    val clubsUpdater = ???
//
    val dividendPayer: DividendPayer[IO] = DividendPayer.impl(
      playerModule.service,
      eventModule.service,
      stateModule.service,
      appConfig
    )

    //todo:
    // to consider:
    // 1. playerValue can changed after match was played - not a big deal, we can use new value
    // 2. sb can buy player stock just for the moment (before the match or even after but before stats are updated)
    // to gain the dividend and then sell stock and buy another for the same reason

//    val transactionOrderFinalizer = ???
    //todo: instead of direct sell/buy transaction user should send transaction order (sell or buy)
    // and this transaction order should:
    // - block expected transaction value (when buying)
    // - be finalized at midnight (?) every day
    // - dividendPayer should start running before (
    //    use case 1:
    // a. player play in game 8pm-10pm
    // b. buy player at 10pm
    // c. transaction finalized at midnight (2h later)
    // d. update player stats at 1 am (1 h later)
    // e. user get dividend
    //    use case 2:
    // a. player play in game 8pm-10pm
    // b. buy player at 10pm
    // c. update player stats at midnight (2 h later)
    // d. transaction finalized at 1 am (1h later)
    // e. user NOT get dividend
    // )

    (gameEngine, playersUpdater, dividendPayer)
  }

  def runBackgroundTasks(
    appConfig: AppConfig,
    gameLogic: GameEngine[IO],
    playersUpdater: PlayersUpdater[IO],
    dividendPayer: DividendPayer[IO]
  ): Resource[IO, ExitCode] = {
    val consolePrinter = ConsolePrinter.impl[IO]

    val gameCliStream: Stream[IO, Unit] =
      fs2
        .Stream
        .repeatEval(consolePrinter.readMessage[IO])
        .evalMap(
          consolePrinter
            .gameLoop(gameLogic)(_)
            .handleErrorWith(err => log.error(err)(s"Game Stream failed. error: ${err.getMessage}"))
        )

    val updatePlayersInMemoryStream: Stream[IO, Unit] =
      fs2
        .Stream
        .awakeEvery[IO](appConfig.updaterTask.playersProfileUpdateEvery)
        .evalMap(_ =>
          playersUpdater
            .updateAllPlayersInMemory
            .handleErrorWith(err => log.error(err)(s"UpdatePlayersInMemory task failed. error: ${err.getMessage}"))
        )

    val updatePlayersValueInUserStatesStream: Stream[IO, Unit] =
      fs2
        .Stream
        .awakeEvery[IO](appConfig.updaterTask.playersValueUpdateEvery)
        .evalMap(_ =>
          playersUpdater
            .updatePlayersValueInUserStates
            .handleErrorWith(err => log.error(err)(s"UpdatePlayersValueInUserStates task failed. error: ${err.getMessage}"))
        )

    val payDividendForPlayersInPortfolio: Stream[IO, Unit] = fs2
      .Stream
      .awakeEvery[IO](appConfig.updaterTask.dividendPayEvery)
      //todo: change it to store lastDividendPayTaskDateTime and start update if:
      // -lastDividendPayTaskDateTime was more than 24h ago or
      // -now is midnight and day is different then lastDividendPayTaskDateTime
      .evalMap(_ =>
        dividendPayer
          .payDividendToAllUsers
          .handleErrorWith(err => log.error(err)(s"payDividendForPlayersInPortfolio task failed. error: ${err.getMessage}"))
      )

    Resource.eval(
      consolePrinter.printStartMessage[IO]
    ) *>
      Resource.eval {
        Stream(
          gameCliStream,
          updatePlayersInMemoryStream,
          updatePlayersValueInUserStatesStream,
          payDividendForPlayersInPortfolio
        )
          .parJoinUnbounded
          .compile
          .drain
          .as(ExitCode.Success)
      }

  }

  def buildDynamoDbClient(aws: AwsConfig): IO[DynamoDbClient] =
    for {
      _ <- log.info(s"Creating DynamoDbClient")
      endpointOverride = aws.endpointOverride
      _ <- log.info(s"endpointOverride: $endpointOverride")
      awsCredentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(aws.accessKey, aws.secretKey))
      client <- IO(
                  DynamoDbClient
                    .builder()
                    .region(aws.region)
                    .endpointOverride(java.net.URI.create(endpointOverride))
                    .credentialsProvider(awsCredentials)
                    .build()
                )
      _      <- log.info(s"DynamoDbClient created")
    } yield client

}
