package testUtils

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import config.AppConfig
import config.AppConfig.PlayersUpdateCriteriaConfig
import game.{GameEngine, PlayersUpdater}
import game.modules.club.service.domain.ClubId
import game.modules.event.Event
import game.modules.login.domain.{TokenData, UserLogin}
import game.modules.player.client.{PlayerProfileClient, PlayerStatsClient}
import game.modules.player.service.domain.PlayerId
import game.modules.state.domain.{StockInfo, User, UserGameState}
import io.circe.Json
import logic.SampleGameSpec
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jFactory
import utils.TimeProvider

import java.time.Instant

object TestUtils {
  implicit val testLoggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  implicit val log: SelfAwareStructuredLogger[IO]   = LoggerFactory.getLoggerFromName[IO](classOf[SampleGameSpec].getName)

  def emptyUserGameState(name: String)(portfolio: List[StockInfo]) = UserGameState(
    user = User(name.toUpperCase),
    portfolio = portfolio,
    money = UserGameState.initialCash,
    updatedAt = Instant.now(),
    wishlist = Nil
  )

  def testGameEngine(
    playerProfileRef: Ref[IO, Map[PlayerId, Json]],
    stateRef: Ref[IO, Map[User, UserGameState]],
    eventRef: Ref[IO, List[Event]],
    clubProfileRef: Ref[IO, Map[ClubId, Json]],
    clubPlayersRef: Ref[IO, Map[ClubId, Json]],
    loginRef: Ref[IO, Map[User, UserLogin]],
    tokenRef: Ref[IO, List[TokenData]],
    playerProfileClient: PlayerProfileClient[IO] = TestPlayerModule.testPlayerProfileClient()
  )(
    implicit timeProvider: TimeProvider[IO]
  ): IO[GameEngine[IO]] = for {
    testRawAppConfig <- AppConfig.getTypesafeConfig[IO]
    appConfig        <- AppConfig.parseAppConfig[IO](testRawAppConfig)
    _                <- log.info(s"Test config loaded: $appConfig")

    testPlayerModule = TestPlayerModule.impl(appConfig, playerProfileRef, playerProfileClient)
    testClubModule   = TestClubModule.impl(appConfig, clubProfileRef, clubPlayersRef)
    testStateModule  = TestStateModule.impl(stateRef)
    testEventModule  = TestEventModule.impl(eventRef)
    testLoginModule = TestLoginModule.impl(appConfig, loginRef, tokenRef)

    gameLogic        = GameEngine.impl(
                         stateService = testStateModule.service,
                         eventService = testEventModule.service,
                         playerService = testPlayerModule.service,
                         clubService = testClubModule.service,
                         loginService = testLoginModule.service
                       )
  } yield gameLogic

  def testPlayersUpdater(
    playerProfileRef: Ref[IO, Map[PlayerId, Json]] = Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json]).unsafeRunSync(),
    stateRef: Ref[IO, Map[User, UserGameState]] = Ref.of[IO, Map[User, UserGameState]](Map.empty[User, UserGameState]).unsafeRunSync(),
    eventRef: Ref[IO, List[Event]] = Ref.of[IO, List[Event]](Nil).unsafeRunSync(),
    clubProfileRef: Ref[IO, Map[ClubId, Json]] = Ref.of[IO, Map[ClubId, Json]](Map.empty[ClubId, Json]).unsafeRunSync(),
    clubPlayersRef: Ref[IO, Map[ClubId, Json]] = Ref.of[IO, Map[ClubId, Json]](Map.empty[ClubId, Json]).unsafeRunSync(),
    playersUpdateCriteria: PlayersUpdateCriteriaConfig = PlayersUpdateCriteriaConfig(2.days),
    playerProfileClient: PlayerProfileClient[IO] = TestPlayerModule.testPlayerProfileClient(),
    playerStatsClient: PlayerStatsClient[IO] = TestPlayerModule.testPlayerStatsClient()
  )(
    implicit timeProvider: TimeProvider[IO]
  ): IO[PlayersUpdater[IO]] = for {
    testRawAppConfig <- AppConfig.getTypesafeConfig[IO]
    appConfig        <- AppConfig.parseAppConfig[IO](testRawAppConfig).map(_.copy(playersUpdateCriteria = playersUpdateCriteria))
    _                <- log.info(s"Test config loaded: $appConfig")

    playerModule = TestPlayerModule.impl(appConfig, playerProfileRef, playerProfileClient, playerStatsClient)
    clubModule   = TestClubModule.impl(appConfig, clubProfileRef, clubPlayersRef)
    stateModule  = TestStateModule.impl(stateRef)
    eventModule  = TestEventModule.impl(eventRef)

    playersUpdater = PlayersUpdater.impl(
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
