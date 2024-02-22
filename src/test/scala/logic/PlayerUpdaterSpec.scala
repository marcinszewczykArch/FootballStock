package logic

import cats.effect.{IO, Ref}
import config.AppConfig.PlayersUpdateCriteriaConfig
import game.modules.event.Event
import game.modules.event.Event.{PlayerValueChanged, PlayersUpdateEvent}
import game.modules.player.client.domain.FetchedPlayerStats
import game.modules.player.client.{PlayerProfileClient, PlayerStatsClient}
import game.modules.player.service.domain.PlayerId
import game.modules.state.domain.{Shares, StockInfo, User, UserGameState}
import io.circe.{Json, parser}
import munit.CatsEffectSuite
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import testUtils._
import utils.{Parser, TimeProvider}

import java.time.Instant
import java.time.temporal.ChronoUnit.{DAYS, SECONDS}

class PlayerUpdaterSpec extends CatsEffectSuite {

  test("playerUpdater.updateAllPlayersInMemory should update players not updated for 2 DAYS or more and send event") {
    for {
      now                                           <- IO.delay(Instant.now())
      implicit0(testTimeProvider: TimeProvider[IO]) <- IO.delay(TestUtils.testTimeProvider(now))
      playerProfileRef                              <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      eventRef                                      <- Ref.of[IO, List[Event]](Nil)
      playersUpdateCriteriaConfig                   <- IO.delay(PlayersUpdateCriteriaConfig(2.days))
      testPlayersUpdater                            <- TestUtils.testPlayersUpdater(
                                                         playerProfileRef = playerProfileRef,
                                                         eventRef = eventRef,
                                                         playersUpdateCriteria = playersUpdateCriteriaConfig,
                                                         playerProfileClient = testPlayerProfileClient()
                                                       )

      playerId1 = toPlayerJson(id = 1)(updatedAt = now)
      playerId2 = toPlayerJson(id = 2)(updatedAt = now.minus(1, DAYS))
      playerId3 = toPlayerJson(id = 3)(updatedAt = now.minus(2, DAYS).plus(1, SECONDS))
      playerId4 = toPlayerJson(id = 4)(updatedAt = now.minus(2, DAYS))
      playerId5 = toPlayerJson(id = 5)(updatedAt = now.minus(2, DAYS).minus(1, SECONDS))
      playerId6 = toPlayerJson(id = 5)(updatedAt = now.minus(3, DAYS))

      _                   <- playerProfileRef.update(
                               _ +
                                 (PlayerId(1) -> playerId1) +
                                 (PlayerId(2) -> playerId2) +
                                 (PlayerId(3) -> playerId3) +
                                 (PlayerId(4) -> playerId4) +
                                 (PlayerId(5) -> playerId5) +
                                 (PlayerId(6) -> playerId6)
                             )
      _                   <- testPlayersUpdater.updateAllPlayersInMemory
      profilesAfterUpdate <- playerProfileRef.get
      updatedPlayerProfilesIds         = profilesAfterUpdate
                                           .filter { case _ -> json => isUpdated(json) }
                                           .map { case id -> _ => id }
                                           .toList
                                           .sorted
      expectedUpdatedPlayerProfilesIds = List(PlayerId(4), PlayerId(5), PlayerId(6))
      _                                = assertEquals(updatedPlayerProfilesIds, expectedUpdatedPlayerProfilesIds)
      events <- eventRef.get
      _             = assertEquals(events.size, 1)
      expectedEvent =
        PlayersUpdateEvent(updateSuccess = expectedUpdatedPlayerProfilesIds, updateFailure = Nil, taskDurationSeconds = 0, timestamp = now)
      _             = assertEquals(events.head, expectedEvent)
    } yield ()
  }

  test("playerUpdater.updateAllPlayersInMemory should NOT update RETIRED players and send event") {
    for {
      now                                           <- IO.delay(Instant.now())
      implicit0(testTimeProvider: TimeProvider[IO]) <- IO.delay(TestUtils.testTimeProvider(now))
      playerProfileRef                              <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      eventRef                                      <- Ref.of[IO, List[Event]](Nil)
      playersUpdateCriteriaConfig                   <- IO.delay(PlayersUpdateCriteriaConfig(2.days))
      testPlayersUpdater                            <- TestUtils.testPlayersUpdater(
                                                         playerProfileRef = playerProfileRef,
                                                         eventRef = eventRef,
                                                         playersUpdateCriteria = playersUpdateCriteriaConfig,
                                                         playerProfileClient = testPlayerProfileClient()
                                                       )

      playerId1 = toPlayerJson(id = 1)(isRetired = true, updatedAt = now)
      playerId2 = toPlayerJson(id = 2)(isRetired = true, updatedAt = now.minus(1, DAYS))
      playerId3 = toPlayerJson(id = 3)(isRetired = true, updatedAt = now.minus(2, DAYS).plus(1, SECONDS))
      playerId4 = toPlayerJson(id = 4)(isRetired = true, updatedAt = now.minus(2, DAYS))
      playerId5 = toPlayerJson(id = 5)(isRetired = true, updatedAt = now.minus(2, DAYS).minus(1, SECONDS))
      playerId6 = toPlayerJson(id = 5)(isRetired = true, updatedAt = now.minus(3, DAYS))

      _                   <- playerProfileRef.update(
                               _ +
                                 (PlayerId(1) -> playerId1) +
                                 (PlayerId(2) -> playerId2) +
                                 (PlayerId(3) -> playerId3) +
                                 (PlayerId(4) -> playerId4) +
                                 (PlayerId(5) -> playerId5) +
                                 (PlayerId(6) -> playerId6)
                             )
      _                   <- testPlayersUpdater.updateAllPlayersInMemory
      profilesAfterUpdate <- playerProfileRef.get
      updatedPlayerProfilesIds         = profilesAfterUpdate
                                           .filter { case _ -> json => isUpdated(json) }
                                           .map { case id -> _ => id }
                                           .toList
                                           .sorted
      expectedUpdatedPlayerProfilesIds = Nil
      _                                = assertEquals(updatedPlayerProfilesIds, expectedUpdatedPlayerProfilesIds)
      events <- eventRef.get
      _             = assertEquals(events.size, 1)
      expectedEvent =
        PlayersUpdateEvent(updateSuccess = expectedUpdatedPlayerProfilesIds, updateFailure = Nil, taskDurationSeconds = 0, timestamp = now)
      _             = assertEquals(events.head, expectedEvent)
    } yield ()
  }

  test("playerUpdater.updatePlayersValueInUserStates should update players value and send events") {
    for {
      now                                           <- IO.delay(Instant.now())
      implicit0(testTimeProvider: TimeProvider[IO]) <- IO.delay(TestUtils.testTimeProvider(now))
      playerProfileRef                              <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      eventRef                                      <- Ref.of[IO, List[Event]](Nil)
      stateRef                                      <- Ref.of[IO, Map[User, UserGameState]](Map.empty[User, UserGameState])

      obsoleteValue                           = "€10.00m"
      currentValue                            = "€20.00m"
      obsoleteValueParsed                     = Parser.toBigDecimalOrZero(Some(obsoleteValue))
      currentValueParsed                      = Parser.toBigDecimalOrZero(Some(currentValue))
      playerId                                = PlayerId(1)
      shares                                  = Shares(
                                                  number = 1,
                                                  buyPrice = 0,
                                                  buyTimestamp = now,
                                                  buyMinutesPlayed = 0,
                                                  minutesPlayedLastSeen = 0,
                                                  dividend = 0
                                                )
      stockInfoObsoleteValue                  = StockInfo(
                                                  playerId = playerId,
                                                  shares = List(shares),
                                                  lastPlayerValue = obsoleteValueParsed,
                                                  lastPlayerMinutesPlayed = 0
                                                )
      stockInfoCurrentValue                   = StockInfo(
                                                  playerId = playerId,
                                                  shares = List(shares),
                                                  lastPlayerValue = currentValueParsed,
                                                  lastPlayerMinutesPlayed = 0
                                                )
      portfolioObsoleteValue: List[StockInfo] = List(stockInfoObsoleteValue)
      portfolioCurrentValue: List[StockInfo]  = List(stockInfoCurrentValue)
      user1State                              = TestUtils.emptyUserGameState("USER1")(portfolioObsoleteValue)
      user2State                              = TestUtils.emptyUserGameState("USER2")(portfolioObsoleteValue)
      user3State                              = TestUtils.emptyUserGameState("USER2")(portfolioCurrentValue)
      _                  <- stateRef.update(
                              _ +
                                (User("USER1") -> user1State) +
                                (User("USER2") -> user2State) +
                                (User("USER3") -> user3State)
                            )
      testPlayersUpdater <- TestUtils.testPlayersUpdater(
                              playerProfileRef = playerProfileRef,
                              stateRef = stateRef,
                              eventRef = eventRef,
                              playerProfileClient = testPlayerProfileClient(currentValue),
                              playerStatsClient = testPlayerStatsClient()
                            )
      _                  <- testPlayersUpdater.updatePlayersValueInUserStates
      events             <- eventRef.get
      _              = assertEquals(events.size, 2)
      expectedEvents = List(
                         PlayerValueChanged(
                           playerId = playerId,
                           playerName = "-",
                           previousValue = obsoleteValueParsed,
                           newValue = currentValueParsed,
                           user = User("USER1"),
                           timestamp = now
                         ),
                         PlayerValueChanged(
                           playerId = playerId,
                           playerName = "-",
                           previousValue = Parser.toBigDecimalOrZero(Some(obsoleteValue)),
                           newValue = Parser.toBigDecimalOrZero(Some(currentValue)),
                           user = User("USER2"),
                           timestamp = now
                         )
                       )
      _              = assertEquals(events, expectedEvents)
      statesAfter <- stateRef.get
      _ = statesAfter.values.toList.flatMap(_.portfolio.map(_.lastPlayerValue)).foreach { newValue =>
            assertEquals(newValue, currentValueParsed)
          }
    } yield ()
  }

  private def testPlayerProfileClient(marketValue: String = "€10.00m"): PlayerProfileClient[IO] = (id: PlayerId) =>
    IO.pure(
      Right(
        parser
          .parse(s"""{
                "id" : "${id.value}",
                "marketValue" : "$marketValue",
                "updated" : true
                }""")
          .toOption
          .get
      )
    )

  private def testPlayerStatsClient(): PlayerStatsClient[IO] = (id: PlayerId) =>
    IO.pure(
      FetchedPlayerStats(
        id = Some(id.value),
        stats = Some(Nil),
        updatedAt = None
      )
    )

  private def isUpdated(playerJson: Json): Boolean =
    playerJson.findAllByKey("updated").headOption.flatMap(_.asBoolean).getOrElse(false)

  private def toPlayerJson(id: Int)(isRetired: Boolean = false, updatedAt: Instant) = parser
    .parse(s"""{
          "id" : "$id",
          "isRetired" : $isRetired,
          "updatedAt" : "${updatedAt.toString}"
          }""")
    .toOption
    .get

}
