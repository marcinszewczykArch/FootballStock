import cats.effect.{ExitCode, IO, IOApp, Ref}
import config.AppConfig
import config.AppConfig.AwsConfig
import console.ConsolePrinter
import fs2.Stream
import game.events.Event
import game.events.memory.EventMemory
import game.gameState.memory.UserGameStateMemory
import game.logic.GameEngine
import game.player.client.{PlayerProfileClient, PlayerSearchClient}
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.{PlayerService, PlayersUpdater, PlayersWriter}
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
import software.amazon.awssdk.auth.credentials.{AwsBasicCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.services.dynamodb.DynamoDbClient
import utils.TimeProvider

import scala.concurrent.duration.DurationInt

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
      dynamoDbClient <- buildDynamoDbClient(appConfig.aws)
      scanamo = Scanamo(dynamoDbClient)
      consolePrinter <- IO.delay(ConsolePrinter.impl[IO])
      _              <- consolePrinter.printStartMessage[IO]

      stateMemory                     <- IO.delay(UserGameStateMemory.impl[IO](scanamo))
      eventMemory                     <- IO.delay(EventMemory.impl[IO](scanamo))
      playerProfileClientMemory       <- IO.delay(PlayerProfileClientMemory.impl[IO](scanamo))
      playerProfileClient             <- IO.delay(PlayerProfileClient.impl[IO](appConfig.playerProfileClient))
      playerProfileClientMemoryCached <-
        IO.delay(
          PlayerProfileClientMemory
            .cachedInstance[IO](appConfig.playerProfileClient, playerProfileClient, playerProfileClientMemory)
        )
      playerSearchClient              <- IO.delay(PlayerSearchClient.cachedInstance[IO](appConfig.playerSearchClient))
      playerService                   <- IO.delay(PlayerService.impl[IO](playerProfileClientMemoryCached, playerSearchClient))
      gameLogic                       <- IO.delay(GameEngine.impl(stateMemory, eventMemory, playerService))
      playersUpdater                  <- IO.delay(
                                           PlayersUpdater.impl[IO](
                                             playerProfileClient,
                                             playerProfileClientMemory,
                                             eventMemory,
                                             appConfig.playersUpdateCriteria
                                           )
                                         )

      playersWriter <- IO.delay(PlayersWriter.impl(playerProfileClient))
      //      _             <- playersWriter.writeToFile(
      //                         path = "src/main/resources/players",
      //                         playerIds = List(38253, 38254, 38255, 38256, 38257)
      //                       )
      exitCode      <- runGame(consolePrinter, playerService, gameLogic, playersUpdater)
    } yield exitCode

  private def runGame(
    consolePrinter: ConsolePrinter[IO],
    playerService: PlayerService[IO],
    gameLogic: GameEngine[IO],
    playersUpdater: PlayersUpdater[IO]
  ): IO[ExitCode] = {
    val gameStream: Stream[IO, Unit] =
      fs2
        .Stream
        .repeatEval(consolePrinter.readMessage[IO])
        .evalMap(consolePrinter.gameLoop(playerService, gameLogic))

    val updatePlayersStream: Stream[IO, Unit] =
      fs2
        .Stream
        .awakeEvery[IO](10.minutes) //todo: from config
        .evalMap(_ => playersUpdater.updatePlayersInMemory)

    Stream(gameStream, updatePlayersStream)
      .parJoinUnbounded
      .compile
      .drain
      .as(ExitCode.Success)
  }

  private def buildDynamoDbClient(aws: AwsConfig): IO[DynamoDbClient] =
    for {
      _      <- log.info(s"Creating DynamoDbClient")
      endpointOverride = "http://0.0.0.0:8000/"
      _      <- log.info(s"endpointOverride: $endpointOverride")
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

}
