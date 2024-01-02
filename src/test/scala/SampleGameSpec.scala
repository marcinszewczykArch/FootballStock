import cats.effect.IO
import cats.effect.Ref
import cats.implicits.toTraverseOps
import game.domain.Shares
import game.domain.UserGameState
import game.events.BuyPlayerEvent
import game.events.InitializeGameEvent
import game.events.SellPlayerEvent
import game.events.UserEvent
import game.logic.GameEngine
import game.memory.EventMemory
import game.memory.StateMemory
import game.player.client.PlayerProfileClient
import game.player.client.PlayerSearchClient
import game.player.client.domain.FetchedMarketValue
import game.player.client.domain.FetchedPlayerProfile
import game.player.client.domain.FetchedPlayerSimple
import game.player.client.domain.PlayerSearchResponse
import game.player.service.PlayerService
import game.player.service.domain.PlayerId
import io.circe.Json
import munit.CatsEffectSuite
import utils.JsonParser.jsonString
import utils.Parser.CaseClassToString
import utils.TimeProvider

import java.time.Instant

class SampleGameSpec extends CatsEffectSuite {

  def getNewGameEngine(implicit timeProvider: TimeProvider[IO]): IO[GameEngine[IO]] = for {
    playerProfileClient <- IO.pure(new PlayerProfileClient[IO] {

                             override def fetchPlayerProfileById(id: PlayerId): IO[FetchedPlayerProfile] = IO.pure(
                               io.circe
                                 .parser
                                 .decode[FetchedPlayerProfile](jsonString("testResponsePlayerProfile.json"))
                                 .toOption
                                 .get
                             )

                             override def fetchRawPlayerProfileById(
                               id: PlayerId
                             ): IO[Json] = ???

      })
    playerSearchClient  <- IO.pure(new PlayerSearchClient[IO] {

                             override def searchByName(playerName: String): IO[List[FetchedPlayerSimple]] =
                               IO.pure(
                                 io.circe
                                   .parser
                                   .decode[PlayerSearchResponse](jsonString("testResponsePlayerSearch.json"))
                                   .toOption
                                   .get
                                   .result
                               )

                           })
    playerService       <- IO.pure(PlayerService.impl[IO](playerProfileClient, playerSearchClient))
    stateRef            <- Ref.of[IO, Map[String, UserGameState]](Map.empty[String, UserGameState])
    stateMemory         <- IO.pure(StateMemory.impl[IO](stateRef))
    eventRef            <- Ref.of[IO, List[UserEvent]](Nil)
    eventMemory         <- IO.pure(EventMemory.impl[IO](eventRef))
    gameLogic           <- IO.pure(GameEngine.impl(stateMemory, eventMemory, playerService))
  } yield gameLogic

  test("Sample game one test") {
    for {
      now                                           <- IO.pure(Instant.now())
      implicit0(testTimeProvider: TimeProvider[IO]) <- IO.pure(new TimeProvider[IO] {
                                                         override def getCurrentTimestamp: Instant = now
                                                       })
      testGameEngine                                <- getNewGameEngine(testTimeProvider)

      _       <- testGameEngine.createUser("Marcin")
      state1  <- testGameEngine.getUserState("Marcin")
      state1Expected = Right(
                         UserGameState(
                           portfolio = Map.empty,
                           money = BigDecimal(1_000_000)
                         )
                       )
      events1 <- testGameEngine.getUserEvents("Marcin")
      events1Expected = Right(List(InitializeGameEvent("Marcin", BigDecimal(1_000_000), now)))
      _ = assertEquals(state1, state1Expected)
      _ = assertEquals(events1, events1Expected)

      transaction1 <- testGameEngine.buyPlayer("Marcin")(PlayerId(38253), 2)
      state2       <- testGameEngine.getUserState("Marcin")
      state2Expected = Right(
                         UserGameState(
                           portfolio = Map(PlayerId(38253) -> List(Shares(2, BigDecimal(30_000_000), now))),
                           money = BigDecimal(400_000)
                         )
                       )
      transaction1Expected = Right(
                               BuyPlayerEvent(
                                 playerId = PlayerId(38253),
                                 shares = 2,
                                 user = "Marcin",
                                 value = BigDecimal(600_000),
                                 timestamp = now
                               )
                             )
      events2      <- testGameEngine.getUserEvents("Marcin")
      events2Expected = for {
                          prev <- events1
                          curr <- transaction1
                        } yield prev :+ curr
      _ = assertEquals(transaction1, transaction1Expected)
      _ = assertEquals(state2, state2Expected)
      _ = assertEquals(events2, events2Expected)

      transaction2 <- testGameEngine.sellPlayer("Marcin")(PlayerId(38253), 1)
      state3       <- testGameEngine.getUserState("Marcin")
      state3Expected = Right(
                         UserGameState(
                           portfolio = Map(PlayerId(38253) -> List(Shares(1, BigDecimal(30_000_000), now))),
                           money = BigDecimal(700_000)
                         )
                       )
      transaction2Expected = Right(
                               SellPlayerEvent(
                                 playerId = PlayerId(38253),
                                 shares = 1,
                                 user = "Marcin",
                                 value = BigDecimal(300_000),
                                 timestamp = now
                               )
                             )
      events3      <- testGameEngine.getUserEvents("Marcin")
      events3Expected = for {
                          prev <- events2
                          curr <- transaction2
                        } yield prev :+ curr
      _ = assertEquals(transaction2, transaction2Expected)
      _ = assertEquals(state3, state3Expected)
      _ = assertEquals(events3, events3Expected)

      userBalance <- testGameEngine.getUserBalance("Marcin")
      _           <- userBalance.right.get.toStringWithFields.map(IO.println).toList.sequence

    } yield ()
  }

}
