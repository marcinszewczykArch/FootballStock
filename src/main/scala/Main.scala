import cats.effect.{ExitCode, IO, IOApp, Ref}
import config.AppConfig
import console.ConsolePrinter
import httpClient.TransfermarktClient
import multiplayer.domain.{Shares, UserGameState}
import multiplayer.logic.GameEngine
import multiplayer.memory.StateMemory
import services.PlayerService
import services.domain.PlayerId

import java.time.Instant

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
      stateMemory         <- IO.pure(StateMemory.impl[IO](ref))
      gameLogic           <- IO.pure(GameEngine.impl(stateMemory, playerService))

      //todo: for test only
      gameState = UserGameState(portfolio = Map(PlayerId(38253) -> List(Shares(5, BigDecimal(20_000_000), Instant.now))))
      _           <- ref.update(_ => Map("marcin" -> gameState))
      userStats   <- gameLogic.getAllUsersStates()
      _           <- IO.println(userStats)
      confBuy     <- gameLogic.buyPlayer("marcin")(PlayerId(38253), 2)
      _           <- IO.println(confBuy)
      confSell    <- gameLogic.sellPlayer("marcin")(PlayerId(38253), 3)
      _           <- IO.println(confSell)
      profile     <- playerService.getPlayerProfileById(PlayerId(38253))
      _           <- IO.println(profile)
      marketValue <- playerService.getMarketValueByPlayerId(PlayerId(38253))
      _           <- IO.println(marketValue)

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
