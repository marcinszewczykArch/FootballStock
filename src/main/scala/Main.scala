import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Ref
import config.AppConfig
import console.ConsolePrinter
import game.domain.UserGameState
import game.events.UserEvent
import game.logic.GameEngine
import game.memory.EventMemory
import game.memory.StateMemory
import game.player.client.{PlayerProfileClient, PlayerSearchClient}
import game.player.service.PlayerService
import game.player.service.domain.PlayerId
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
      playerProfileClient <- IO.pure(PlayerProfileClient.cachedInstance[IO](appConfig.transfermarktClient))
      playerSearchClient  <- IO.pure(PlayerSearchClient.cachedInstance[IO](appConfig.playerSearchClient))
      playerService       <- IO.pure(PlayerService.impl[IO](playerProfileClient, playerSearchClient))
      stateRef            <- Ref.of[IO, Map[String, UserGameState]](Map.empty[String, UserGameState])
      stateMemory         <- IO.pure(StateMemory.impl[IO](stateRef))
      eventRef            <- Ref.of[IO, List[UserEvent]](Nil)
      eventMemory         <- IO.pure(EventMemory.impl[IO](eventRef))
      gameLogic           <- IO.pure(GameEngine.impl(stateMemory, eventMemory, playerService))

      //todo: for test only
      _           <- gameLogic.createUser("marcin")
      userStats   <- gameLogic.getAllUsersStates()
      _           <- IO.println(userStats)
      confBuy     <- gameLogic.buyPlayer("marcin")(PlayerId(38253), 2)
      _           <- IO.println(confBuy)
      confSell    <- gameLogic.sellPlayer("marcin")(PlayerId(38253), 1)
      _           <- IO.println(confSell)
      profile     <- playerService.getPlayerProfileById(PlayerId(38253))
      _           <- IO.println(profile)
      marketValue <- playerService.getMarketValueByPlayerId(PlayerId(38253))
      _           <- IO.println(marketValue)
      balance     <- gameLogic.getUserBalance("marcin")
      _           <- IO.println(balance)

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
      .as(ExitCode.Success)

}
