import cats.effect.IO
import cats.effect.Ref
import cats.implicits.toTraverseOps
import config.AppConfig.TransfermarktClientConfig
import game.gameState.Shares
import game.gameState.UserGameState
import game.events.BuyPlayerEvent
import game.events.InitializeGameEvent
import game.events.SellPlayerEvent
import game.events.UserEvent
import game.events.memory.{EventMemory}
import game.gameState.memory.StateMemory
import game.logic.GameEngine
import game.player.client.PlayerProfileClient
import game.player.client.PlayerSearchClient
import game.player.client.domain.FetchedPlayerProfile
import game.player.client.domain.FetchedPlayerSimple
import game.player.client.domain.PlayerSearchResponse
import game.player.service.PlayerService
import game.player.service.domain.PlayerId
import munit.CatsEffectSuite
import org.http4s.LiteralSyntaxMacros.uri
import sttp.client3.UriContext
import utils.JsonParser.jsonString
import utils.Parser.CaseClassToString
import utils.TimeProvider
import sttp.model.Uri

import java.time.Instant
import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.SECONDS

class PlayerProfileClientSpec extends CatsEffectSuite {

  import io.circe.Json
  import io.circe._, io.circe.parser._

  test("Client profile spec") {
    for {
      now                                           <- IO.pure(Instant.now())
      implicit0(testTimeProvider: TimeProvider[IO]) <- IO.pure(new TimeProvider[IO] {
                                                         override def getCurrentTimestamp: Instant = now
                                                       })
      playerProfileClient                           <- IO.pure(PlayerProfileClient.impl[IO](
                                                         TransfermarktClientConfig(
                                                           uri = uri"https://transfermarkt-api.vercel.app",
                                                           cacheTtl = FiniteDuration(300, SECONDS),
                                                           failedCacheTtl = FiniteDuration(300, SECONDS),
                                                           cacheName = "transfermarkt.player-profile-client"
                                                         )
                                                       ))

      fetchedJson <- playerProfileClient.fetchRawPlayerProfileById(PlayerId(38253))
      expectedJsonStr = jsonString("testResponsePlayerProfile.json")
      expectedJson = parse(expectedJsonStr).toOption.get

      _ = assertEquals(fetchedJson, expectedJson)
    } yield ()
  }

}
