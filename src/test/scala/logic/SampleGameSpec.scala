package logic

import cats.effect.IO
import cats.effect.Ref
import cats.implicits.toTraverseOps
import config.AppConfig
import game.events.Event
import game.events.Event.BuyPlayerEvent
import game.events.Event.InitializeGameEvent
import game.events.Event.SellPlayerEvent
import game.events.service.EventService
import game.logic.GameEngine
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerService
import game.player.service.domain.PlayerId
import game.state.domain.Shares
import game.state.domain.User
import game.state.domain.UserGameState
import game.state.service.UserGameStateService
import io.circe.Json
import munit.CatsEffectSuite
import org.typelevel.log4cats.slf4j.Slf4jFactory
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import testUtils.TestUtils
import utils.Parser.CaseClassToString
import utils.TimeProvider

import java.time.Instant

class SampleGameSpec extends CatsEffectSuite {
  private implicit val testLoggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  implicit val log: SelfAwareStructuredLogger[IO]           = LoggerFactory.getLoggerFromName[IO](classOf[SampleGameSpec].getName)

  def getNewGameEngine(
    playerProfileRef: Ref[IO, Map[PlayerId, Json]],
    stateRef: Ref[IO, Map[User, UserGameState]],
    eventRef: Ref[IO, List[Event]]
  )(
    implicit timeProvider: TimeProvider[IO]
  ): IO[GameEngine[IO]] = for {
    //config
    testRawAppConfig <- AppConfig.getTypesafeConfig[IO]
    appConfig        <- AppConfig.parseAppConfig[IO](testRawAppConfig)
    _                <- log.info(s"Test config loaded: $appConfig")

    //memory, clients
    testPlayerProfileClient                 <- TestUtils.testPlayerProfileClient()
    testPlayerSearchClient                  <- TestUtils.testPlayerSearchClient()
    testPlayerProfileClientMemoryUnderlying <- TestUtils.testPlayerProfileClientMemory(playerProfileRef)
    playerProfileClientMemoryCached         <-
      IO.delay(
        PlayerProfileClientMemory
          .cachedInstance(appConfig.playerProfileClient, testPlayerProfileClient, testPlayerProfileClientMemoryUnderlying)
      )
    testStateMemory                         <- TestUtils.testUserGameStateMemory(stateRef)
    testEventMemory                         <- TestUtils.testEventMemory(eventRef)

    //services
    eventService  = EventService.impl(testEventMemory)
    stateService  = UserGameStateService.impl(testStateMemory)
    playerService = PlayerService.impl[IO](playerProfileClientMemoryCached, testPlayerSearchClient)
    gameLogic     = GameEngine.impl(stateService, eventService, playerService)
  } yield gameLogic

  test("Sample game test") {
    for {
      now                                           <- IO.delay(Instant.now())
      implicit0(testTimeProvider: TimeProvider[IO]) <- TestUtils.testTimeProvider(now)
      playerProfileRef                              <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      stateRef                                      <- Ref.of[IO, Map[User, UserGameState]](Map.empty[User, UserGameState])
      eventRef                                      <- Ref.of[IO, List[Event]](Nil)
      testGameEngine                                <- getNewGameEngine(playerProfileRef, stateRef, eventRef)
      testUser = User("TestUserName")

      _      <- testGameEngine.createUser(testUser)
      state1 <- testGameEngine.getUserState(testUser)
      state1Expected = Right(
                         UserGameState(
                           portfolio = Map.empty,
                           money = BigDecimal(1_000_000),
                           updatedAt = now
                         )
                       )
      events1 <- testGameEngine.getUserEvents(testUser)
      events1Expected = Right(List(InitializeGameEvent(BigDecimal(1_000_000), testUser, now)))
      _               = assertEquals(state1, state1Expected)
      _               = assertEquals(events1, events1Expected)

      transaction1 <- testGameEngine.buyPlayer(testUser)(PlayerId(38253), 2)
      state2       <- testGameEngine.getUserState(testUser)
      state2Expected       = Right(
                               UserGameState(
                                 portfolio = Map(PlayerId(38253) -> List(Shares(2, BigDecimal(30_000_000), now))),
                                 money = BigDecimal(400_000),
                                 updatedAt = now
                               )
                             )
      transaction1Expected = Right(
                               BuyPlayerEvent(
                                 playerId = PlayerId(38253),
                                 playerName = "Lewandowski",
                                 shares = 2,
                                 user = testUser,
                                 value = BigDecimal(600_000),
                                 timestamp = now
                               )
                             )
      events2 <- testGameEngine.getUserEvents(testUser)
      events2Expected = for {
                          prev <- events1
                          curr <- transaction1
                        } yield prev :+ curr
      _               = assertEquals(transaction1, transaction1Expected)
      _               = assertEquals(state2, state2Expected)
      _               = assertEquals(events2, events2Expected)

      transaction2 <- testGameEngine.sellPlayer(testUser)(PlayerId(38253), 1)
      state3       <- testGameEngine.getUserState(testUser)
      state3Expected       = Right(
                               UserGameState(
                                 portfolio = Map(PlayerId(38253) -> List(Shares(1, BigDecimal(30_000_000), now))),
                                 money = BigDecimal(700_000),
                                 updatedAt = now
                               )
                             )
      transaction2Expected = Right(
                               SellPlayerEvent(
                                 playerId = PlayerId(38253),
                                 playerName = "Lewandowski",
                                 shares = 1,
                                 user = testUser,
                                 value = BigDecimal(300_000),
                                 timestamp = now
                               )
                             )
      events3 <- testGameEngine.getUserEvents(testUser)
      events3Expected = for {
                          prev <- events2
                          curr <- transaction2
                        } yield prev :+ curr
      _               = assertEquals(transaction2, transaction2Expected)
      _               = assertEquals(state3, state3Expected)
      _               = assertEquals(events3, events3Expected)

      userBalance <- testGameEngine.getUserBalance(testUser)
      _           <- userBalance.right.get.toStringWithFields.map(IO.println).toList.sequence

    } yield ()
  }

}
