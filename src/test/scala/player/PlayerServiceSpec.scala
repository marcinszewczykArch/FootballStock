package player

import cats.effect.IO
import cats.effect.Ref
import config.AppConfig
import game.modules.player.client.domain.{FetchedPlayerProfile, PlayerSearchResponse}
import game.modules.player.service.{PlayerMapper, PlayerService}
import game.modules.player.service.domain.{PlayerId, PlayerProfile}
import PlayerMapper.fetchedPlayerSimpleToPlayerSimple
import io.circe.Json
import io.circe.parser
import munit.CatsEffectSuite
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import org.typelevel.log4cats.slf4j.Slf4jFactory
import testUtils._
import utils.JsonParser

class PlayerServiceSpec extends CatsEffectSuite {
  private implicit val testLoggerFactory: LoggerFactory[IO] = Slf4jFactory.create[IO]
  implicit val log: SelfAwareStructuredLogger[IO]           = LoggerFactory.getLoggerFromName[IO](classOf[PlayerServiceSpec].getName)

  test("getPlayerProfileById spec") {
    for {
      playerProfileClientMemoryRef <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      playerService                <- getTestPlayerService(playerProfileClientMemoryRef)

      fetchedPlayer38253 <- playerService.getPlayerProfileById(PlayerId(38253))
      expectedPlayer38253 = expectedPlayerFromPath("playerProfile/38253.json")
      _                   = assertEquals(fetchedPlayer38253.toOption.get, expectedPlayer38253)

      fetchedPlayer38254 <- playerService.getPlayerProfileById(PlayerId(38254))
      expectedPlayer38254 = expectedPlayerFromPath("playerProfile/38254.json")
      _                   = assertEquals(fetchedPlayer38254.toOption.get, expectedPlayer38254)

      fetchedPlayer38255 <- playerService.getPlayerProfileById(PlayerId(38255))
      expectedPlayer38255 = expectedPlayerFromPath("playerProfile/38255.json")
      _                   = assertEquals(fetchedPlayer38255.toOption.get, expectedPlayer38255)
    } yield ()
  }

  test("getMarketValueByPlayerId spec") {
    for {
      playerProfileClientMemoryRef <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      playerService                <- getTestPlayerService(playerProfileClientMemoryRef)

      fetchedMarketValue38253 <- playerService.getMarketValueByPlayerId(PlayerId(38253))
      expectedMarketValue38253 = expectedPlayerFromPath("playerProfile/38253.json").marketValue
      _                        = assertEquals(fetchedMarketValue38253.toOption.get, expectedMarketValue38253)

    } yield ()
  }

  test("searchByName spec") {
    for {
      playerProfileClientMemoryRef <- Ref.of[IO, Map[PlayerId, Json]](Map.empty[PlayerId, Json])
      playerService                <- getTestPlayerService(playerProfileClientMemoryRef)

      fetchedResult <- playerService.searchByName("anyInputShouldWork")
      expectedString = JsonParser.jsonString("playerSearch/testResponsePlayerSearch.json")
      expectedJson   = parser.parse(expectedString).toOption.get
      expectedResult = expectedJson.as[PlayerSearchResponse].toOption.get.result.map(fetchedPlayerSimpleToPlayerSimple)

      _ = assertEquals(fetchedResult.toOption.get, expectedResult)
    } yield ()
  }

  private def getTestPlayerService(playerProfileClientMemoryRef: Ref[IO, Map[PlayerId, Json]]): IO[PlayerService[IO]] = for {
    testRawAppConfig <- AppConfig.getTypesafeConfig[IO]
    appConfig        <- AppConfig.parseAppConfig[IO](testRawAppConfig)
    _                <- log.info(s"Test config loaded: $appConfig")
    playerModule = TestPlayerModule.impl(appConfig, playerProfileClientMemoryRef)
  } yield playerModule.service

  private def expectedPlayerFromPath(path: String): PlayerProfile = {
    val playerString          = JsonParser.jsonString(path)
    val playerJson            = parser.parse(playerString).toOption.get
    val expectedPlayerProfile = PlayerMapper.fetchedPlayerProfileToProfile(playerJson.as[FetchedPlayerProfile].toOption.get)
    expectedPlayerProfile
  }

}
