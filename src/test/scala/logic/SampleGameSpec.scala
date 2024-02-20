package logic

import cats.effect.{IO, Ref}
import cats.implicits.toTraverseOps
import game.modules.event.Event.{BuyPlayerEvent, InitializeGameEvent, SellPlayerEvent}
import game.modules.club.service.domain.ClubId
import game.modules.event.Event
import game.modules.player.service.domain.PlayerId
import game.modules.state.domain.{Shares, StockInfo, User, UserGameState}
import io.circe.Json
import munit.CatsEffectSuite
import testUtils._
import utils.Parser.CaseClassToString
import utils.TimeProvider

import java.time.Instant

class SampleGameSpec extends CatsEffectSuite {

  test("Sample game test") {
    for {
      now                                           <- IO.delay(Instant.now())
      implicit0(testTimeProvider: TimeProvider[IO]) <- IO.delay(TestUtils.testTimeProvider(now))
      playerProfileRef                              <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      stateRef                                      <- Ref.of[IO, Map[User, UserGameState]](Map.empty[User, UserGameState])
      eventRef                                      <- Ref.of[IO, List[Event]](Nil)
      clubProfileRef                                <- Ref.of[IO, Map[ClubId, Json]](Map.empty[ClubId, Json])
      clubPlayersRef                                <- Ref.of[IO, Map[ClubId, Json]](Map.empty[ClubId, Json])
      testGameEngine                                <- TestUtils.testGameEngine(playerProfileRef, stateRef, eventRef, clubProfileRef, clubPlayersRef)
      testUser = User("TestUserName")

      _      <- testGameEngine.createUser(testUser)
      state1 <- testGameEngine.getUserState(testUser)
      state1Expected = Right(
                         UserGameState(
                           user = testUser,
                           portfolio = Nil,
                           money = BigDecimal(1_000_000),
                           updatedAt = now,
                           Nil
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
                                 user = testUser,
                                 portfolio = List(
                                   StockInfo(
                                     playerId = PlayerId(38253),
                                     shares = List(
                                       Shares(
                                         number = 2,
                                         buyPrice = BigDecimal(3.0e+7),
                                         buyTimestamp = now,
                                         buyMinutesPlayed = 0,
                                         minutesPlayedLastSeen = 0,
                                         dividend = 0
                                       )
                                     ),
                                     lastPlayerValue = BigDecimal(3.0e+7),
                                     lastPlayerMinutesPlayed = 0
                                   )
                                 ),
                                 money = BigDecimal(400_000),
                                 updatedAt = now,
                                 wishlist = Nil
                               )
                             )
      transaction1Expected = Right(
                               BuyPlayerEvent(
                                 playerId = PlayerId(38253),
                                 playerName = "Robert Lewandowski",
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
                                 user = testUser,
                                 portfolio = List(
                                   StockInfo(
                                     playerId = PlayerId(38253),
                                     shares = List(
                                       Shares(
                                         number = 1,
                                         buyPrice = BigDecimal(30_000_000),
                                         buyTimestamp = now,
                                         buyMinutesPlayed = 0,
                                         minutesPlayedLastSeen = 0,
                                         dividend = 0
                                       )
                                     ),
                                     lastPlayerValue = BigDecimal(3.0e+7),
                                     lastPlayerMinutesPlayed = 0
                                   )
                                 ),
                                 money = BigDecimal(700_000),
                                 updatedAt = now,
                                 wishlist = Nil
                               )
                             )
      transaction2Expected = Right(
                               SellPlayerEvent(
                                 playerId = PlayerId(38253),
                                 playerName = "Robert Lewandowski",
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
