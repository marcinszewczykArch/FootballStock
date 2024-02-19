package player

import cats.effect.{IO, Ref}
import game.club.service.domain.ClubId
import game.event.Event
import game.player.service.domain.PlayerId
import game.state.domain.{User, UserGameState}
import io.circe.Json
import munit.CatsEffectSuite
import testUtils._
import utils.TimeProvider

import java.time.Instant

class PlayerUpdaterSpec extends CatsEffectSuite {

  test("playerUpdater spec") {
    for {
      now                                           <- IO.delay(Instant.now())
      implicit0(testTimeProvider: TimeProvider[IO]) <- IO.delay(TestUtils.testTimeProvider(now))
      playerProfileRef                              <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      stateRef                                      <- Ref.of[IO, Map[User, UserGameState]](Map.empty[User, UserGameState])
      eventRef                                      <- Ref.of[IO, List[Event]](Nil)
      clubProfileRef                                <- Ref.of[IO, Map[ClubId, Json]](Map.empty[ClubId, Json])
      clubPlayersRef                                <- Ref.of[IO, Map[ClubId, Json]](Map.empty[ClubId, Json])
      testPlayersUpdater                            <- TestUtils.testPlayersUpdater(playerProfileRef, stateRef, eventRef, clubProfileRef, clubPlayersRef)

//      fetchedJson <- playerProfileClient.fetchRawPlayerProfileById(PlayerId(38253))
//      expectedJsonStr = jsonString("testResponsePlayerProfile.json")
//      expectedJson = parse(expectedJsonStr).toOption.get
//
//      _ = assertEquals(fetchedJson.toOption.get, expectedJson)
    } yield ()
  }

}
