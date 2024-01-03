import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Ref
import cats.effect.kernel.Clock
import config.AppConfig
import console.ConsolePrinter
import game.events.UserEvent
import game.events.memory.EventMemory
import game.gameState.UserGameState
import game.gameState.memory.StateMemory
import game.logic.GameEngine
import game.player.client.PlayerProfileClient
import game.player.client.PlayerSearchClient
import game.player.memory.PlayerProfileClientMemory
import game.player.service.PlayerService
import game.player.service.PlayersLoader
import game.player.service.domain.PlayerId
import io.circe.Json
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.slf4j.Slf4jFactory
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
      consolePrinter <- IO.delay(ConsolePrinter.impl[IO])
      _              <- consolePrinter.printStartMessage[IO]

      //memory - to be replaced by DynamoDb
      stateRef         <- Ref.of[IO, Map[String, UserGameState]](Map.empty[String, UserGameState])
      eventRef         <- Ref.of[IO, List[UserEvent]](Nil)
      playerProfileRef <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])

      stateMemory                     <- IO.delay(StateMemory.impl[IO](stateRef))
      eventMemory                     <- IO.delay(EventMemory.impl[IO](eventRef))
      playerProfileClient             <- IO.delay(PlayerProfileClient.impl[IO](appConfig.transfermarktClient))
      playerProfileClientMemory       <- IO.delay(PlayerProfileClientMemory.impl[IO](playerProfileRef))
      playerProfileClientMemoryCached <-
        IO.delay(PlayerProfileClientMemory.cachedInstance[IO](appConfig.transfermarktClient, playerProfileClient, playerProfileClientMemory))
      playerSearchClient              <- IO.delay(PlayerSearchClient.cachedInstance[IO](appConfig.playerSearchClient))
      playerService                   <- IO.delay(PlayerService.impl[IO](playerProfileClientMemoryCached, playerSearchClient))
      gameLogic                       <- IO.delay(GameEngine.impl(stateMemory, eventMemory, playerService))

      playersLoader <- IO.delay(PlayersLoader.impl[IO](playerProfileClient, playerProfileClientMemory))
      _             <- playersLoader.loadPlayersToMemory(1, 100)

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

}
