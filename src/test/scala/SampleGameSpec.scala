import cats.effect.{IO, Ref}
import httpClient.TransfermarktClient
import httpClient.domain.{FetchedMarketValue, FetchedPlayerProfile, FetchedPlayerSimple}
import io.circe
import multiplayer.domain.UserGameState
import multiplayer.logic.GameEngine
import multiplayer.memory.StateMemory
import munit.CatsEffectSuite
import services.PlayerService
import services.domain.PlayerId
import sttp.client3.ResponseException

class SampleGameSpec extends CatsEffectSuite {

  def getNewGameEngine(): IO[GameEngine[IO]] = for {
    transfermarktClient <- IO.pure(new TransfermarktClient[IO] {

        override def searchByName(playerName: String): IO[Either[
                               ResponseException[String, circe.Error],
                               List[FetchedPlayerSimple]
                             ]] = ???

        override def fetchMarketValueByPlayerId(id: PlayerId): IO[Either[
          ResponseException[String, circe.Error],
          FetchedMarketValue
        ]] = ???

        override def fetchPlayerProfileById(id: PlayerId): IO[Either[
          ResponseException[String, circe.Error],
          FetchedPlayerProfile
        ]] = ???

      })
    playerService       <- IO.pure(PlayerService.impl[IO](transfermarktClient))
    ref                 <- Ref.of[IO, Map[String, UserGameState]](Map.empty[String, UserGameState])
    stateMemory         <- IO.pure(StateMemory.impl[IO](ref))
    gameLogic           <- IO.pure(GameEngine.impl(stateMemory, playerService))
  } yield gameLogic

  test("Sample game one test") {
    for {
      gameEngine: GameEngine[IO] <- getNewGameEngine()

//      johnShotResultNo1 <- gameEngine.shoot("JohnDoe", "a1")
//      _ = assertEquals(johnShotResultNo1.get.isHit, true)
//      _ = assertEquals(johnShotResultNo1.get.getShipType, Some(TwoDecker))
//      _ = assertEquals(johnShotResultNo1.get.isSunk, Some(false))
//      _ = assertEquals(johnShotResultNo1.get.getNumberOfShots, 1)

    } yield ()
  }

}
