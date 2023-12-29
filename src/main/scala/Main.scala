import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Ref
import config.AppConfig
import console.ConsolePrinter
import game.domain.Shares
import game.domain.UserGameState
import game.events.UserEvent
import game.logic.GameEngine
import game.memory.EventMemory
import game.memory.StateMemory
import game.player.client.TransfermarktClient
import game.player.service.PlayerService
import game.player.service.domain.PlayerId
import utils.TimeProvider

object Main extends IOApp {
  implicit val timeProvider = TimeProvider.impl[IO]

  def run(args: List[String]): IO[ExitCode] =
    for {
      rawAppConfig        <- AppConfig.getTypesafeConfig
      appConfig           <- AppConfig.parse(rawAppConfig)
      consolePrinter      <- IO.pure(ConsolePrinter.impl[IO])
      _                   <- consolePrinter.printStartMessage[IO]
      transfermarktClient <- IO.pure(TransfermarktClient.impl[IO](appConfig.transfermarktClientConfig))
      playerService       <- IO.pure(PlayerService.impl[IO](transfermarktClient))
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
