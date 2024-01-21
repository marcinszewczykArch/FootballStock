import cats.effect._
import cats.implicits.toSemigroupKOps
import config.AppConfig
import config.AppConfig.AwsConfig
import config.AppConfig.HttpConfig
import console.ConsolePrinter
import fs2.Stream
import game.errors.GameException
import game.events.memory.EventMemory
import game.events.service.EventService
import game.logic.GameEngine
import game.player.client.PlayerProfileClient
import game.player.client.PlayerSearchClient
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerService
import game.player.service.PlayersUpdater
import game.state.memory.UserGameStateMemory
import game.state.service.UserGameStateService
import http.SwaggerRoutes
import http.gameState.GameStateLogic
import http.gameState.GameStateRoutes
import http.player.PlayerProfileLogic
import http.player.PlayerProfileRoutes
import http.security.EloTokenVerification
import http.security.TokenVerification
import org.http4s.BuildInfo
import org.http4s.HttpRoutes
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Router
import org.http4s.server.Server
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import utils.TimeProvider

import scala.concurrent.duration.DurationInt

object Main extends IOApp {
  implicit val timeProvider: TimeProvider[IO]           = TimeProvider.impl[IO]
  implicit val loggerFactory: LoggerFactory[IO]         = Slf4jFactory.create[IO]
  implicit val tokenVerification: TokenVerification[IO] = EloTokenVerification
  type Result[A] = Either[GameException, A] //todo: this can be used in multiple places for simplification
  private val log = LoggerFactory.getLoggerFromClass(classOf[Main.type])

  def run(args: List[String]): IO[ExitCode] =
    (for {
      //config
      _              <- Resource.eval(log.info("starting..."))
      rawAppConfig   <- Resource.eval(AppConfig.getTypesafeConfig[IO])
      appConfig      <- Resource.eval(AppConfig.parseAppConfig[IO](rawAppConfig))
      _              <- Resource.eval(log.info(s"config loaded: $appConfig"))
      dynamoDbClient <- Resource.eval(buildDynamoDbClient(appConfig.aws))

      //memory, clients
      scanamo                         = Scanamo(dynamoDbClient)
      consolePrinter                  = ConsolePrinter.impl[IO]
      stateMemory                     = UserGameStateMemory.impl[IO](scanamo)
      eventMemory                     = EventMemory.impl[IO](scanamo)
      playerProfileClientMemory       = PlayerProfileClientMemory.impl[IO](scanamo)
      playerProfileClient             = PlayerProfileClient.impl[IO](appConfig.playerProfileClient)
      playerProfileClientMemoryCached =
        PlayerProfileClientMemory.cachedInstance[IO](appConfig.playerProfileClient, playerProfileClient, playerProfileClientMemory)
      playerSearchClient              = PlayerSearchClient.cachedInstance[IO](appConfig.playerSearchClient)

      //services
      gameStateService = UserGameStateService.impl[IO](stateMemory)
      eventService     = EventService.impl(eventMemory)
      playerService    = PlayerService.impl[IO](playerProfileClientMemoryCached, playerSearchClient)
      playersUpdater   = PlayersUpdater.impl[IO](
                           playerProfileClient,
                           playerProfileClientMemory,
                           eventMemory,
                           appConfig.playersUpdateCriteria
                         )
      gameEngine       = GameEngine.impl(gameStateService, eventService, playerService)
      gameStateLogic   = GameStateLogic.impl[IO](gameEngine)
      playerProfileLogic   = PlayerProfileLogic.impl[IO](gameEngine)

      //server
      _ <- httpServerResource(appConfig, gameStateLogic, playerProfileLogic)
      _ <- Resource.eval(consolePrinter.printStartMessage[IO])
      _ <- Resource.eval(runGame(consolePrinter, gameEngine, playersUpdater))
    } yield ()).useForever

  private def runGame(
    consolePrinter: ConsolePrinter[IO],
    gameLogic: GameEngine[IO],
    playersUpdater: PlayersUpdater[IO]
  ): IO[ExitCode] = {
    val gameStream: Stream[IO, Unit] =
      fs2
        .Stream
        .repeatEval(consolePrinter.readMessage[IO])
        .evalMap(consolePrinter.gameLoop(gameLogic))

    val updatePlayersInMemoryStream: Stream[IO, Unit] =
      fs2
        .Stream
        .awakeEvery[IO](10.seconds) //todo: from config
        .evalMap(_ => playersUpdater.updateAllPlayersInMemory)

    Stream(gameStream, updatePlayersInMemoryStream)
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
    gameStateLogic: GameStateLogic[IO],
    playerProfileLogic: PlayerProfileLogic[IO]
  )(
    implicit tokenVerification: TokenVerification[IO]
  ): Resource[IO, Server] = for {
    swaggerRoutes       <- Resource.eval(SwaggerRoutes.routes)
    gameStateRoutes     <- Resource.eval(new GameStateRoutes[IO](tokenVerification).routes(gameStateLogic))
    playerProfileRoutes <- Resource.eval(new PlayerProfileRoutes[IO](tokenVerification).routes(playerProfileLogic))
    server              <- buildServer(swaggerRoutes <+> gameStateRoutes <+> playerProfileRoutes, appConfig.http)
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
                  .withHttpApp(Router("/" -> routes).orNotFound)
                  .build
      _      <- Resource.eval(log.info(s"Started $BuildInfo HTTP server"))
      _      <- Resource.eval(IO.println(s"Go to http://localhost:${server.address.getPort}/swagger to open SwaggerUI"))
    } yield server

}
