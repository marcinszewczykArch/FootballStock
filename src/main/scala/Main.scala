import cats.effect.{ExitCode, IO, IOApp, Ref}
import config.AppConfig
import config.AppConfig.AwsConfig
import console.ConsolePrinter
import game.events.UserEvent
import game.events.memory.EventMemory
import game.gameState.UserGameState
import game.gameState.memory.StateMemory
import game.logic.GameEngine
import game.player.client.{PlayerProfileClient, PlayerSearchClient}
import game.player.memory.PlayerProfileClientMemory
import game.player.service.{PlayerService, PlayersLoader}
import game.player.service.domain.PlayerId
import io.circe.Json
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import utils.TimeProvider

object Main extends IOApp {
  implicit val timeProvider: TimeProvider[IO] = TimeProvider.impl[IO]
  private implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val log = LoggerFactory.getLoggerFromClass(classOf[Main.type])

  def run(args: List[String]): IO[ExitCode] =
    for {
      _              <- log.info("starting...")
      rawAppConfig   <- AppConfig.getTypesafeConfig[IO]
      appConfig      <- AppConfig.parseAppConfig[IO](rawAppConfig)
      _              <- log.info(s"config loaded: $appConfig")
      dynamoDbClient <- buildDynamoDbClient(appConfig.awsConfig)
      scanamo = Scanamo(dynamoDbClient)
      consolePrinter <- IO.delay(ConsolePrinter.impl[IO])
      _              <- consolePrinter.printStartMessage[IO]

      //memory - to be replaced by DynamoDb
      stateRef         <- Ref.of[IO, Map[String, UserGameState]](Map.empty[String, UserGameState])
      eventRef         <- Ref.of[IO, List[UserEvent]](Nil)
      playerProfileRef <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])

      stateMemory                       <- IO.delay(StateMemory.impl[IO](stateRef))
      eventMemory                       <- IO.delay(EventMemory.impl[IO](eventRef))
      playerProfileClient               <- IO.delay(PlayerProfileClient.impl[IO](appConfig.transfermarktClient))
//      playerProfileClientMemoryRef      <- IO.delay(PlayerProfileClientMemory.implRef[IO](playerProfileRef))
      playerProfileClientMemoryDynamoDb <- IO.delay(PlayerProfileClientMemory.implDynamoDb[IO](scanamo))
      playerProfileClientMemoryCached   <-
        IO.delay(
          PlayerProfileClientMemory
            .cachedInstance[IO](appConfig.transfermarktClient, playerProfileClient, playerProfileClientMemoryDynamoDb)
        )
      playerSearchClient                <- IO.delay(PlayerSearchClient.cachedInstance[IO](appConfig.playerSearchClient))
      playerService                     <- IO.delay(PlayerService.impl[IO](playerProfileClientMemoryCached, playerSearchClient))
      gameLogic                         <- IO.delay(GameEngine.impl(stateMemory, eventMemory, playerService))

      playersLoader <- IO.delay(PlayersLoader.impl[IO](playerProfileClient, playerProfileClientMemoryDynamoDb))
      _             <- playersLoader.loadPlayersToMemory(38253, 38255)

      exitCode <- runGame(consolePrinter, playerService, gameLogic)
    } yield exitCode

  private def runGame(
    consolePrinter: ConsolePrinter[IO],
    playerService: PlayerService[IO],
    gameLogic: GameEngine[IO]
  ): IO[ExitCode] =
    fs2
      .Stream
      .repeatEval(consolePrinter.readMessage[IO])
      .evalMap(consolePrinter.gameLoop(playerService, gameLogic))
      .compile
      .drain
      .as(ExitCode.Success) //todo: concatenate with stream refreshing player state memory once a day

  def buildDynamoDbClient(aws: AwsConfig): IO[DynamoDbClient] =
    for {
      _      <- log.info(s"Creating DynamoDbClient")
      localhostIp = java.net.InetAddress.getLocalHost.getHostAddress.toString
      _      <- log.info(s"localhostIp: $localhostIp")
//      endpointOverride = "http://" + localhostIp + ":8000/"
      endpointOverride = "http://" + "0.0.0.0" + ":8000/"
      _      <- log.info(s"endpointOverride: $endpointOverride")
      awsCredentials = StaticCredentialsProvider.create(AwsBasicCredentials.create(aws.awsAccessKey, aws.awsSecretKey))
      client <- IO(
                  DynamoDbClient
                    .builder()
                    .region(aws.awsRegion)
                    .endpointOverride(java.net.URI.create(endpointOverride)) //todo: endpoint override for local
                    .credentialsProvider(awsCredentials)
                    .build()
                )
      _      <- log.info(s"DynamoDbClient created")
    } yield client

}
