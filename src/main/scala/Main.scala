import cats.effect._
import cats.implicits.toSemigroupKOps
import config.AppConfig
import config.AppConfig._
import console.ConsolePrinter
import fs2.Stream
import game.{GameEngine, GameException}
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
  implicit val timeProvider: TimeProvider[IO]           = TimeProvider.impl[IO]
  implicit val loggerFactory: LoggerFactory[IO]         = Slf4jFactory.create[IO]
  implicit val tokenVerification: TokenVerification[IO] = EloTokenVerification
  val corsService: CORSPolicy = CORS.policy.withAllowOriginAll

  private val log = LoggerFactory.getLoggerFromClass(classOf[Main.type])

  def run(args: List[String]): IO[ExitCode] =
    (for {
      //config and resources
      _              <- Resource.eval(log.info("starting..."))
      rawAppConfig   <- Resource.eval(AppConfig.getTypesafeConfig[IO])
      appConfig      <- Resource.eval(AppConfig.parseAppConfig[IO](rawAppConfig))
      _              <- Resource.eval(log.info(s"config loaded: $appConfig"))
      dynamoDbClient <- Resource.eval(buildDynamoDbClient(appConfig.aws))
      scanamo        = Scanamo(dynamoDbClient)
      consolePrinter = ConsolePrinter.impl[IO]

      //modules
      playerModule = PlayerModule.impl[IO](scanamo, appConfig)
      clubModule   = ClubModule.impl[IO](scanamo, appConfig)
      stateModule  = StateModule.impl[IO](scanamo)
      eventModule  = EventModule.impl[IO](scanamo)

      playersUpdater = PlayersUpdater.impl[IO](
                         playerModule.playerProfileClient,
                         playerModule.playerProfileClientMemory,
                         playerModule.playerProfileClientMemoryCached,
                         playerModule.service,
                         eventModule.service,
                         stateModule.service,
                         appConfig.playersUpdateCriteria
                       )

      gameEngine = GameEngine.impl(stateModule.service, eventModule.service, playerModule.service, clubModule.service)

      //server
      _ <- httpServerResource(appConfig, gameEngine)
      _ <- Resource.eval(consolePrinter.printStartMessage[IO])
      _ <- Resource.eval(runGame(consolePrinter, gameEngine, playersUpdater, appConfig.updaterTask))
    } yield ()).useForever

  private def runGame(
    consolePrinter: ConsolePrinter[IO],
    gameLogic: GameEngine[IO],
    playersUpdater: PlayersUpdater[IO],
    updaterTask: UpdaterTaskConfig
  ): IO[ExitCode] = {
    val gameStream: Stream[IO, Unit] =
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
        .awakeEvery[IO](updaterTask.playersProfileUpdateEvery)
        .evalMap(_ =>
          playersUpdater
            .updateAllPlayersInMemory
            .handleErrorWith(err => log.error(err)(s"UpdatePlayersInMemory task failed. error: ${err.getMessage}"))
        )

    val updatePlayersValueInUserStatesStream: Stream[IO, Unit] =
      fs2
        .Stream
        .awakeEvery[IO](updaterTask.playersValueUpdateEvery)
        .evalMap(_ =>
          playersUpdater
            .updatePlayersValueInUserStates
            .handleErrorWith(err => log.error(err)(s"UpdatePlayersValueInUserStates task failed. error: ${err.getMessage}"))
        )

    Stream(gameStream, updatePlayersInMemoryStream, updatePlayersValueInUserStatesStream)
      .parJoinUnbounded
      .compile
      .drain
      .as(ExitCode.Success)
  }

  private def buildDynamoDbClient(aws: AwsConfig): IO[DynamoDbClient] =
    for {
      _ <- log.info(s"Creating DynamoDbClient")
      endpointOverride = "http://0.0.0.0:8000/"
      _ <- log.info(s"endpointOverride: $endpointOverride")
      awsCredentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(aws.accessKey, aws.secretKey))
      client <- IO(
                  DynamoDbClient
                    .builder()
                    .region(aws.region)
                    .endpointOverride(java.net.URI.create(endpointOverride)) //todo: endpoint override for local only <- co config or sthing
                    .credentialsProvider(awsCredentials)
                    .build()
                )
      _      <- log.info(s"DynamoDbClient created")
    } yield client

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
    server              <- buildServer(swaggerRoutes <+> gameStateRoutes <+> playerProfileRoutes <+> eventRoutes, appConfig.http)
  } yield server

  def buildServer(
    routes: HttpRoutes[IO],
    httpConfig: HttpConfig
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

}
