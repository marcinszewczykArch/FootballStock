import cats.effect.IO
import cats.effect.Ref
import httpClient.TransfermarktClient
import httpClient.domain.FetchedMarketValue
import httpClient.domain.FetchedPlayerProfile
import httpClient.domain.FetchedPlayerSimple
import httpClient.domain.PlayerSearchResponse
import io.circe
import multiplayer.domain.Shares
import multiplayer.domain.UserGameState
import multiplayer.logic.GameEngine
import multiplayer.memory.StateMemory
import munit.CatsEffectSuite
import services.PlayerService
import services.domain.PlayerId
import sttp.client3.ResponseException
import utils.JsonParser.jsonString

import java.time.Instant

class SampleGameSpec extends CatsEffectSuite {

  def getNewGameEngine(): IO[GameEngine[IO]] = for {
    transfermarktClient <- IO.pure(new TransfermarktClient[IO] {

                             override def searchByName(playerName: String): IO[Either[
                               ResponseException[String, circe.Error],
                               List[FetchedPlayerSimple]
                             ]] =
                               IO.pure(
                                 Right[ResponseException[String, circe.Error], List[FetchedPlayerSimple]](
                                   io.circe
                                     .parser
                                     .decode[PlayerSearchResponse](jsonString("testResponseMarketValue.json"))
                                     .toOption
                                     .get
                                     .result
                                 )
                               )

                             override def fetchMarketValueByPlayerId(id: PlayerId): IO[Either[
                               ResponseException[String, circe.Error],
                               FetchedMarketValue
                             ]] = IO.pure(
                               Right[ResponseException[String, circe.Error], FetchedMarketValue](
                                 io.circe
                                   .parser
                                   .decode[FetchedMarketValue](jsonString("testResponsePlayerProfile.json"))
                                   .toOption
                                   .get
                               )
                             )

                             override def fetchPlayerProfileById(id: PlayerId): IO[Either[
                               ResponseException[String, circe.Error],
                               FetchedPlayerProfile
                             ]] = IO.pure(
                               Right[ResponseException[String, circe.Error], FetchedPlayerProfile](
                                 io.circe
                                   .parser
                                   .decode[FetchedPlayerProfile](jsonString("testResponsePlayerSearch.json"))
                                   .toOption
                                   .get
                               )
                             )

                           })
    playerService <- IO.pure(PlayerService.impl[IO](transfermarktClient))
    ref           <- Ref.of[IO, Map[String, UserGameState]](Map.empty[String, UserGameState])
    stateMemory   <- IO.pure(StateMemory.impl[IO](ref))
    gameLogic     <- IO.pure(GameEngine.impl(stateMemory, playerService))
  } yield gameLogic

  test("Sample game one test") {
    for {
      now <- IO.pure(Instant.now())
//      testTimeProvider <- TestTimeProvider(now)
      testGameEngine <- getNewGameEngine()

      _            <- testGameEngine.createUser("Marcin")
      marcinState1 <- testGameEngine.getUserState("Marcin")
      _ = assertEquals(marcinState1.map(_.money), Right(BigDecimal(1_000_000)))

      buy1         <- testGameEngine.buyPlayer("Marcin")(PlayerId(38253), 2)
      _ = assertEquals(buy1.map(_.shares), Right(2))
      _ = assertEquals(buy1.map(_.value), Right(BigDecimal(600_000)))
      marcinState2 <- testGameEngine.getUserState("Marcin")
      _ = assertEquals(marcinState2.map(_.money), Right(BigDecimal(400_000)))
      _ = assertEquals(marcinState2.map(_.portfolio.size), Right(1))
      _ = assertEquals(marcinState2.map(_.portfolio(PlayerId(38253))), Right(List(Shares(2, BigDecimal(30_000_000), now))))
    } yield ()
  }

}
