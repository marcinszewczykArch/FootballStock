import FootballStockApp._
import cats.effect._
import cats.implicits._
import config.AppConfig
import config.AppConfig._
import console.ConsolePrinter
import fs2.Stream
import game.GameEngine
import game.club.ClubModule
import game.event.EventModule
import game.player.PlayerModule
import game.player.service.PlayersUpdater
import game.state.StateModule
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
      (gameEngine, playersUpdater) = getGameEngine(appConfig, dynamoDbClient)
      _ <- httpServerResource(appConfig, gameEngine)
      _ <- runBackgroundTasks(appConfig, gameEngine, playersUpdater)
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

  def getGameEngine(
    appConfig: AppConfig,
    dynamoDbClient: DynamoDbClient
  )(
    implicit timeProvider: TimeProvider[IO]
  ): (GameEngine[IO], PlayersUpdater[IO]) = {
    val scanamo = Scanamo(dynamoDbClient)

    //modules
    val playerModule = PlayerModule.impl[IO](scanamo, appConfig)
    val clubModule   = ClubModule.impl[IO](scanamo, appConfig)
    val stateModule  = StateModule.impl[IO](scanamo)
    val eventModule  = EventModule.impl[IO](scanamo)

    val playersUpdater = PlayersUpdater.impl[IO](
      playerModule.playerProfileClient,
      playerModule.playerProfileClientMemory,
      playerModule.playerProfileClientMemoryCached,
      playerModule.service,
      eventModule.service,
      stateModule.service,
      appConfig.playersUpdateCriteria
    )

    //todo: clubsUpdater to be implemented like once a day

    val gameEngine =
      GameEngine.impl(stateModule.service, eventModule.service, playerModule.service, clubModule.service)

    (gameEngine, playersUpdater)
  }

  def runBackgroundTasks(
    appConfig: AppConfig,
    gameLogic: GameEngine[IO],
    playersUpdater: PlayersUpdater[IO]
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

//    val payDividendForPlayersInPortfolio: Stream[IO, Unit] = ???
    //1. iterate thorough all user states
    //2. iterate thorough all user players in portfolio
    //3. iterate thorough all stock packages for player
    //4. pay dividend if (MinutesPlayed - MinutesPlayedLastSeen) > 0
    //5. increment dividend by value:
    // val value = currentPlayerValue * (numberOfStock * 0.01) * 0.01 * (MinutesPlayed - MinutesPlayedLastSeen)/90
    //6. Add value to cash
    //7. Update MinutesPlayedLastSeen to MinutesPlayed

    //to consider:
    //1. playerValue can changed after match was played - not a big deal, we can use new value
    //2. sb can buy player stock just for the moment (before the match or even after but before stats are updated)
    // to gain the dividend and then sell stock and buy another for the same reason

    Resource.eval(
      consolePrinter.printStartMessage[IO]
    ) *>
      Resource.eval {
        Stream(gameCliStream, updatePlayersInMemoryStream, updatePlayersValueInUserStatesStream)
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
