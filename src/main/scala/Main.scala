import cats.effect.{ExitCode, IO, IOApp, Resource}
import com.softwaremill.hiring_task.AppConfig
import console.ConsolePrinter
import httpClient.TransfermarktClient

object Main extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    for {
      rawAppConfig        <- AppConfig.getTypesafeConfig
      appConfig           <- AppConfig.parse(rawAppConfig)
      consolePrinter      <- IO.pure(ConsolePrinter.impl[IO])
      _                   <- consolePrinter.printStartMessage[IO]
      transfermarktClient <- IO.pure(TransfermarktClient.impl[IO](appConfig.transfermarktClientConfig))
      exitCode            <- runGame(consolePrinter, transfermarktClient)
    } yield exitCode

  private def runGame(
    consolePrinter: ConsolePrinter[IO],
    transfermarktClient: TransfermarktClient[IO]
  ): IO[ExitCode] =
    fs2
      .Stream
      .repeatEval(consolePrinter.readMessage[IO])
      .evalMap(consolePrinter.gameLoop(transfermarktClient))
      .compile
      .drain
      .as(ExitCode.Success)

}
