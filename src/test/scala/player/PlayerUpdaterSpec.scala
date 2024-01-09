package player

import cats.effect.IO
import cats.effect.Ref
import config.AppConfig
import config.AppConfig.PlayersUpdateCriteriaConfig
import game.events.Event
import game.events.memory.EventMemory
import game.player.service.PlayersUpdater
import game.player.service.domain.PlayerId
import io.circe.Json
import munit.CatsEffectSuite
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jFactory
import testUtils.TestUtils
import utils.TimeProvider

import java.time.Instant
import scala.concurrent.duration.DurationInt

class PlayerUpdaterSpec extends CatsEffectSuite {

  private implicit val testLoggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  implicit val log: SelfAwareStructuredLogger[IO] = LoggerFactory.getLoggerFromName[IO](classOf[PlayerServiceSpec].getName)

  def getTestPlayersUpdater(
    playersUpdateCriteria: PlayersUpdateCriteriaConfig,
    playerProfileClientMemoryRef: Ref[IO, Map[PlayerId, Json]],
    eventMemoryRef: Ref[IO, List[Event]]
  )(
    implicit timeProvider: TimeProvider[IO]
  ): IO[PlayersUpdater[IO]] = for {
    testRawAppConfig                        <- AppConfig.getTypesafeConfig[IO]
    appConfig                               <- AppConfig.parseAppConfig[IO](testRawAppConfig)
    _                                       <- log.info(s"Test config loaded: $appConfig")
    testPlayerProfileClient                 <- TestUtils.testPlayerProfileClient()
    testPlayerProfileClientMemoryUnderlying <- TestUtils.testPlayerProfileClientMemory(playerProfileClientMemoryRef)
    testEventMemory                         <- TestUtils.testEventMemory(eventMemoryRef)
    playersUpdater                          <- IO.delay(
                                                 PlayersUpdater.impl[IO](
                                                   playerProfileClient = testPlayerProfileClient,
                                                   playerProfileClientMemory = testPlayerProfileClientMemoryUnderlying,
                                                   eventMemory = testEventMemory,
                                                   playersUpdateCriteria = playersUpdateCriteria
                                                 )
                                               )
  } yield playersUpdater

  test("updatePlayersInMemory spec") {
    for {
      now                                           <- IO.delay(Instant.now())
      implicit0(testTimeProvider: TimeProvider[IO]) <- TestUtils.testTimeProvider(now)
      playersUpdateCriteria = PlayersUpdateCriteriaConfig(notUpdatedFor = 10.seconds)
      playerProfileClientMemoryRef                  <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      eventMemoryRef                                <- Ref.of[IO, List[Event]](Nil)
      testPlayersUpdater                            <- getTestPlayersUpdater(
                                                         playersUpdateCriteria,
                                                         playerProfileClientMemoryRef,
                                                         eventMemoryRef
                                                       )

//      fetchedJson <- playerProfileClient.fetchRawPlayerProfileById(PlayerId(38253))
//      expectedJsonStr = jsonString("testResponsePlayerProfile.json")
//      expectedJson = parse(expectedJsonStr).toOption.get
//
//      _ = assertEquals(fetchedJson.toOption.get, expectedJson)
    } yield ()
  }

}
