import cats.effect.IO
import cats.effect.Ref
import cats.implicits.toTraverseOps
import httpClient.TransfermarktClient
import httpClient.domain.FetchedMarketValue
import httpClient.domain.FetchedPlayerProfile
import httpClient.domain.FetchedPlayerSimple
import httpClient.domain.PlayerSearchResponse
import io.circe
import multiplayer.domain.{BuyPlayerEvent, UserEventType, InitializeGameEvent, SellPlayerEvent, Shares, UserGameState}
import multiplayer.logic.GameEngine
import multiplayer.memory.StateMemory
import munit.CatsEffectSuite
import services.PlayerService
import services.domain.PlayerId
import sttp.client3.ResponseException
import utils.JsonParser.jsonString
import utils.Parser.CaseClassToString
import utils.TimeProvider

import java.time.Instant

class SampleGameSpec extends CatsEffectSuite {

  def getNewGameEngine(implicit timeProvider: TimeProvider[IO]): IO[GameEngine[IO]] = for {
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
      now                                           <- IO.pure(Instant.now())
      implicit0(testTimeProvider: TimeProvider[IO]) <- IO.pure(new TimeProvider[IO] {
                                                         override def getCurrentTimestamp: Instant = now
                                                       })
      testGameEngine                                <- getNewGameEngine(testTimeProvider)

      _      <- testGameEngine.createUser("Marcin")
      state1 <- testGameEngine.getUserState("Marcin")
      state1Expected = Right(
                         UserGameState(
                           portfolio = Map.empty,
                           money = BigDecimal(1_000_000),
                           events = List(InitializeGameEvent(BigDecimal(1_000_000), now))
                         )
                       )
      _ = assertEquals(state1, state1Expected)

      transaction1 <- testGameEngine.buyPlayer("Marcin")(PlayerId(38253), 2)
      state2       <- testGameEngine.getUserState("Marcin")
      state2Expected = Right(
                         UserGameState(
                           portfolio = Map(PlayerId(38253) -> List(Shares(2, BigDecimal(30_000_000), now))),
                           money = BigDecimal(400_000),
                           events = state1Expected.value.events :+ transaction1.toOption.get
                         )
                       )
      transaction1Expected = Right(
                               BuyPlayerEvent(
                                 playerId = PlayerId(38253),
                                 shares = 2,
                                 value = BigDecimal(600_000),
                                 timestamp = now
                               )
                             )
      _ = assertEquals(transaction1, transaction1Expected)
      _ = assertEquals(state2, state2Expected)

      transaction2 <- testGameEngine.sellPlayer("Marcin")(PlayerId(38253), 1)
      state3       <- testGameEngine.getUserState("Marcin")
      state3Expected = Right(
                         UserGameState(
                           portfolio = Map(PlayerId(38253) -> List(Shares(1, BigDecimal(30_000_000), now))),
                           money = BigDecimal(700_000),
                           events = state2Expected.value.events :+ transaction2.toOption.get
                         )
                       )
      transaction2Expected = Right(
                               SellPlayerEvent(
                                 playerId = PlayerId(38253),
                                 shares = 1,
                                 value = BigDecimal(300_000),
                                 timestamp = now
                               )
                             )
      _ = assertEquals(transaction2, transaction2Expected)
      _ = assertEquals(state3, state3Expected)

      userBalance <- testGameEngine.getUserBalance("Marcin")
      _           <- userBalance.right.get.toStringWithFields.map(IO.println).toList.sequence

    } yield ()
  }

}
