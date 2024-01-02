import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Ref
import cats.effect.kernel.Clock
import config.AppConfig
import console.ConsolePrinter
import game.domain.UserGameState
import game.events.UserEvent
import game.logic.GameEngine
import game.memory.EventMemory
import game.memory.PlayerProfileClientMemory
import game.memory.StateMemory
import game.player.client.PlayerProfileClient
import game.player.client.PlayerSearchClient
import game.player.service.PlayerService
import game.player.service.PlayersLoader
import game.player.service.domain.PlayerId
import io.circe.Json
import org.typelevel.log4cats.LoggerFactory
import utils.TimeProvider
import org.typelevel.log4cats.slf4j.Slf4jFactory

object Main extends IOApp {
  implicit val timeProvider: TimeProvider[IO] = TimeProvider.impl[IO]
  private implicit val loggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  private val log = LoggerFactory.getLoggerFromClass(classOf[Main.type])

  def run(args: List[String]): IO[ExitCode] =
    for {
      _                   <- log.info("starting...")
      rawAppConfig        <- AppConfig.getTypesafeConfig[IO]
      appConfig           <- AppConfig.parseAppConfig[IO](rawAppConfig)
      consolePrinter      <- IO.pure(ConsolePrinter.impl[IO])
      _                   <- consolePrinter.printStartMessage[IO]

      //memory - to be replaced by DynamoDb
      stateRef            <- Ref.of[IO, Map[String, UserGameState]](Map.empty[String, UserGameState])
      eventRef            <- Ref.of[IO, List[UserEvent]](Nil)
      playerProfileRef    <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])

      stateMemory         <- IO.pure(StateMemory.impl[IO](stateRef))
      eventMemory         <- IO.pure(EventMemory.impl[IO](eventRef))
      playerProfileMemory <- IO.pure(PlayerProfileClientMemory.impl[IO](playerProfileRef))

      playerProfileClient <- IO.pure(PlayerProfileClient.cachedInstance[IO](appConfig.transfermarktClient, playerProfileMemory))
      playerSearchClient  <- IO.pure(PlayerSearchClient.cachedInstance[IO](appConfig.playerSearchClient))
      playerService       <- IO.pure(PlayerService.impl[IO](playerProfileClient, playerSearchClient))
      gameLogic           <- IO.pure(GameEngine.impl(stateMemory, eventMemory, playerService))

      playersLoader <- IO.pure(PlayersLoader.impl[IO](playerProfileClient))
      (duration, _) <- Clock[IO].timed {
                         playersLoader.loadPlayersToCache(1, 100)
                       }
      _             <- log.info(s"loaded 100 players in ${duration.toSeconds} seconds.")

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
