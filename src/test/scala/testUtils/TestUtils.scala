package testUtils

import cats.effect.{IO, Ref}
import config.AppConfig
import game.GameEngine
import game.club.service.domain.ClubId
import game.event.Event
import game.player.service.PlayersUpdater
import game.player.service.domain.PlayerId
import game.state.domain.{User, UserGameState}
import io.circe.Json
import logic.SampleGameSpec
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jFactory
import utils.TimeProvider

import java.time.Instant

object TestUtils {
  implicit val testLoggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  implicit val log: SelfAwareStructuredLogger[IO]   = LoggerFactory.getLoggerFromName[IO](classOf[SampleGameSpec].getName)

  def testGameEngine(
    playerProfileRef: Ref[IO, Map[PlayerId, Json]],
    stateRef: Ref[IO, Map[User, UserGameState]],
    eventRef: Ref[IO, List[Event]],
    clubProfileRef: Ref[IO, Map[ClubId, Json]],
    clubPlayersRef: Ref[IO, Map[ClubId, Json]]
  )(
    implicit timeProvider: TimeProvider[IO]
  ): IO[GameEngine[IO]] = for {
    testRawAppConfig <- AppConfig.getTypesafeConfig[IO]
    appConfig        <- AppConfig.parseAppConfig[IO](testRawAppConfig)
    _                <- log.info(s"Test config loaded: $appConfig")

    testPlayerModule = TestPlayerModule.impl(appConfig, playerProfileRef)
    testClubModule   = TestClubModule.impl(appConfig, clubProfileRef, clubPlayersRef)
    testStateModule  = TestStateModule.impl(stateRef)
    testEventModule  = TestEventModule.impl(eventRef)
    gameLogic        = GameEngine.impl(
                         stateService = testStateModule.service,
                         eventService = testEventModule.service,
                         playerService = testPlayerModule.service,
                         clubService = testClubModule.service
                       )
  } yield gameLogic

  def testPlayersUpdater(
    playerProfileRef: Ref[IO, Map[PlayerId, Json]],
    stateRef: Ref[IO, Map[User, UserGameState]],
    eventRef: Ref[IO, List[Event]],
    clubProfileRef: Ref[IO, Map[ClubId, Json]],
    clubPlayersRef: Ref[IO, Map[ClubId, Json]]
  )(
    implicit timeProvider: TimeProvider[IO]
  ): IO[PlayersUpdater[IO]] = for {
    testRawAppConfig <- AppConfig.getTypesafeConfig[IO]
    appConfig        <- AppConfig.parseAppConfig[IO](testRawAppConfig)
    _                <- log.info(s"Test config loaded: $appConfig")

    playerModule = TestPlayerModule.impl(appConfig, playerProfileRef)
    clubModule   = TestClubModule.impl(appConfig, clubProfileRef, clubPlayersRef)
    stateModule  = TestStateModule.impl(stateRef)
    eventModule  = TestEventModule.impl(eventRef)

    playersUpdater = PlayersUpdater.impl[IO](
                       playerModule.playerProfileClient,
                       playerModule.playerProfileClientMemory,
                       playerModule.playerProfileClientMemoryCached,
                       playerModule.service,
                       eventModule.service,
                       stateModule.service,
                       appConfig.playersUpdateCriteria
                     )
  } yield playersUpdater

  def testTimeProvider(now: Instant) = new TimeProvider[IO] {
    override def getCurrentTimestamp: Instant = now
  }

}
