import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.IOApp
import cats.effect.Ref
import cats.effect.Resource
import config.AppConfig
import console.ConsolePrinter
import httpClient.TransfermarktClient
import multiplayer.UserGameState
import multiplayer.memory.StateMemory
import services.PlayerService

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    for {
      rawAppConfig        <- AppConfig.getTypesafeConfig
      appConfig           <- AppConfig.parse(rawAppConfig)
      consolePrinter      <- IO.pure(ConsolePrinter.impl[IO])
      _                   <- consolePrinter.printStartMessage[IO]
      transfermarktClient <- IO.pure(TransfermarktClient.impl[IO](appConfig.transfermarktClientConfig))
      playerService       <- IO.pure(PlayerService.impl[IO](transfermarktClient))
      ref                 <- Ref.of[IO, Map[String, UserGameState]](Map.empty[String, UserGameState])

      //todo: for test only
      stateMemory <- IO.pure(StateMemory.impl[IO](ref, playerService))
      _           <- ref.update(_ => Map("marcin" -> UserGameState(portfolio = Map(38253 -> 0.01))))
      userStats   <- stateMemory.getAllUsersStates()
      _           <- IO.println(userStats)
      confBuy     <- stateMemory.buyPlayer("marcin")(38253, 0.01)
      _           <- IO.println(confBuy)
      confSell    <- stateMemory.sellPlayer("marcin")(38253, 0.01)
      _           <- IO.println(confSell)

      exitCode <- runGame(consolePrinter, playerService)
    } yield exitCode

  private def runGame(
    consolePrinter: ConsolePrinter[IO],
    playerService: PlayerService[IO]
  ): IO[ExitCode] =
    fs2
      .Stream
      .repeatEval(consolePrinter.readMessage[IO])
      .evalMap(consolePrinter.gameLoop(playerService))
      .compile
      .drain
      .as(ExitCode.Success)

}
