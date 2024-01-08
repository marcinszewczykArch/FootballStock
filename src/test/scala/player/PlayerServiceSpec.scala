package player

import cats.effect.{IO, Ref}
import config.AppConfig
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerService
import game.player.service.domain.PlayerId
import io.circe.Json
import munit.CatsEffectSuite
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import org.typelevel.log4cats.slf4j.Slf4jFactory
import testUtils._

class PlayerServiceSpec extends CatsEffectSuite {
  private implicit val testLoggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  implicit val log: SelfAwareStructuredLogger[IO] = LoggerFactory.getLoggerFromName[IO](classOf[PlayerServiceSpec].getName)

  def getTestPlayerService(playerProfileClientMemoryRef: Ref[IO, Map[PlayerId, Json]]): IO[PlayerService[IO]] = for {
    testRawAppConfig                        <- AppConfig.getTypesafeConfig[IO]
    appConfig                               <- AppConfig.parseAppConfig[IO](testRawAppConfig)
    _                                       <- log.info(s"Test config loaded: $appConfig")
    testPlayerProfileClient                 <- TestUtils.testPlayerProfileClient()
    testPlayerSearchClient                  <- TestUtils.testPlayerSearchClient()
    testPlayerProfileClientMemoryUnderlying <- TestUtils.testPlayerProfileClientMemory(playerProfileClientMemoryRef)
    playerProfileClientMemoryCached         <-
      IO.delay(
        PlayerProfileClientMemory
          .cachedInstance(appConfig.playerProfileClient, testPlayerProfileClient, testPlayerProfileClientMemoryUnderlying)
      )
    playerService                           <- IO.delay(PlayerService.impl[IO](playerProfileClientMemoryCached, testPlayerSearchClient))
  } yield playerService

  test("getPlayerProfileById spec") {
    for {
      playerProfileClientMemoryRef <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      playerService                <- getTestPlayerService(playerProfileClientMemoryRef)

//      fetchedJson <- playerProfileClient.fetchRawPlayerProfileById(PlayerId(38253))
//      expectedJsonStr = jsonString("testResponsePlayerProfile.json")
//      expectedJson = parse(expectedJsonStr).toOption.get
//
//      _ = assertEquals(fetchedJson.toOption.get, expectedJson)
    } yield ()
  }

  test("getMarketValueByPlayerId spec") {
    for {
      playerProfileClientMemoryRef <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      playerService                <- getTestPlayerService(playerProfileClientMemoryRef)

      //      fetchedJson <- playerProfileClient.fetchRawPlayerProfileById(PlayerId(38253))
      //      expectedJsonStr = jsonString("testResponsePlayerProfile.json")
      //      expectedJson = parse(expectedJsonStr).toOption.get
      //
      //      _ = assertEquals(fetchedJson.toOption.get, expectedJson)
    } yield ()
  }

  test("searchByName spec") {
    for {
      playerProfileClientMemoryRef <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      playerService                <- getTestPlayerService(playerProfileClientMemoryRef)

      //      fetchedJson <- playerProfileClient.fetchRawPlayerProfileById(PlayerId(38253))
      //      expectedJsonStr = jsonString("testResponsePlayerProfile.json")
      //      expectedJson = parse(expectedJsonStr).toOption.get
      //
      //      _ = assertEquals(fetchedJson.toOption.get, expectedJson)
    } yield ()
  }

}
