package testUtils

import cats.data.EitherT
import cats.effect.{IO, Ref}
import config.AppConfig
import game.GameException
import game.GameException.{PlayerMarketValueHistoryClientException, PlayerProfileClientException, PlayerProfileJsonNotFoundInMemoryCacheException, PlayerStatsClientException}
import game.player.PlayerModule
import game.player.client.domain.{FetchedMarketValueHistory, FetchedPlayerSimple, FetchedPlayerStats, PlayerSearchResponse}
import game.player.client.{PlayerMarketValueClient, PlayerProfileClient, PlayerSearchClient, PlayerStatsClient}
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.PlayerService
import game.player.service.domain.PlayerId
import io.circe.{Json, parser}
import org.typelevel.log4cats.LoggerFactory
import utils.JsonParser.jsonString
import utils.Type.ErrorOr

object TestPlayerModule {

  def impl(
    appConfig: AppConfig,
    ref: Ref[IO, Map[PlayerId, Json]]
  )(
    implicit loggerFactory: LoggerFactory[IO]
  ): PlayerModule[IO] = new PlayerModule[IO] {
    override val playerProfileClientMemory       = testPlayerProfileClientMemory(ref)
    override val playerProfileClient             = testPlayerProfileClient()
    override val playerProfileClientMemoryCached = PlayerProfileClientMemory
      .cachedInstance(appConfig.playerProfileClient, playerProfileClient, playerProfileClientMemory)

    val playerSearchClient       = testPlayerSearchClient()
    val playerSearchClientCached = PlayerSearchClient.cachedInstance(appConfig.playerSearchClient, playerSearchClient)

    val playerMarketValueClient       = testPlayerMarketValueClient()
    val playerMarketValueClientCached =
      PlayerMarketValueClient.cachedInstance(appConfig.playerMarketValueClient, playerMarketValueClient)

    val playerStatsClient       = testPlayerStatsClient()
    val playerStatsClientCached = PlayerStatsClient.cachedInstance(appConfig.playerStatsClient, playerStatsClient)

    override val service = PlayerService.impl(
      playerProfileClientMemoryCached,
      playerProfileClient,
      playerSearchClientCached,
      playerMarketValueClientCached,
      playerStatsClientCached
    )

  }

  private def testPlayerProfileClient(): PlayerProfileClient[IO] = (id: PlayerId) =>
    IO.pure(
      parser.parse(jsonString(s"playerProfile/${id.value}.json")) match {
        case Right(json)          => Right(json)
        case Left(parsingFailure) => Left(PlayerProfileClientException(parsingFailure.getMessage()))
      }
    )

  private def testPlayerSearchClient(): PlayerSearchClient[IO] =
    (playerName: String) =>
      IO.pure(
        io.circe
          .parser
          .decode[PlayerSearchResponse](jsonString("playerSearch/testResponsePlayerSearch.json"))
          .toOption
          .get
          .result
      )

  private def testPlayerMarketValueClient(): PlayerMarketValueClient[IO] = (id: PlayerId) =>
    IO.pure(
      parser.parse(jsonString(s"playerMarketValue/${id.value}.json")) match {
        case Right(json)          =>
          json.as[FetchedMarketValueHistory].getOrElse(throw PlayerMarketValueHistoryClientException("decoding json failure"))
        case Left(parsingFailure) => throw PlayerMarketValueHistoryClientException(parsingFailure.getMessage())
      }
    )

  private def testPlayerStatsClient(): PlayerStatsClient[IO] = (id: PlayerId) =>
    IO.pure(
      parser.parse(jsonString(s"playerStats/${id.value}.json")) match {
        case Right(json)          => json.as[FetchedPlayerStats].getOrElse(throw PlayerStatsClientException("decoding json failure"))
        case Left(parsingFailure) => throw PlayerStatsClientException(parsingFailure.getMessage())
      }
    )

  private def testPlayerProfileClientMemory(
    ref: Ref[IO, Map[PlayerId, Json]]
  ): PlayerProfileClientMemory[IO] =
    new PlayerProfileClientMemory[IO] {

      override def save(playerId: PlayerId)(playerJson: Json): IO[ErrorOr[Unit]] = (for {
        json <- EitherT.right[GameException](ref.update(_ + (playerId -> playerJson)))
      } yield json).value

      override def getById(playerId: PlayerId): IO[ErrorOr[Json]] = ref
        .get
        .map(_.get(playerId) match {
          case Some(playerJson) => Right(playerJson)
          case None             => Left(PlayerProfileJsonNotFoundInMemoryCacheException(playerId))
        })

      override def getAll(): IO[Map[PlayerId, Json]] = ref.get

    }

}
