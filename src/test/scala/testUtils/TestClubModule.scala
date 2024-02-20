package testUtils

import cats.data.EitherT
import cats.effect.{IO, Ref}
import config.AppConfig
import game.GameException
import game.GameException.{ClubPlayersClientException, ClubPlayersJsonNotFoundInMemoryCacheException, ClubProfileClientException, ClubProfileJsonNotFoundInMemoryCacheException}
import game.modules.club.ClubModule
import game.modules.club.client.{ClubPlayersClient, ClubProfileClient, ClubSearchClient}
import game.modules.club.client.domain.{ClubSearchResponse, FetchedClubSimple}
import game.modules.club.client.memory.{ClubPlayersClientMemory, ClubProfileClientMemory}
import game.modules.club.service.ClubService
import game.modules.club.service.domain.ClubId
import io.circe.{Json, parser}
import org.typelevel.log4cats.LoggerFactory
import utils.JsonParser.jsonString
import utils.Type.ErrorOr

object TestClubModule {

  def impl(
    appConfig: AppConfig,
    clubProfileRef: Ref[IO, Map[ClubId, Json]],
    clubPlayersRef: Ref[IO, Map[ClubId, Json]]
  )(
    implicit loggerFactory: LoggerFactory[IO]
  ) = new ClubModule[IO] {
    val clubProfileClientMemory       = testClubProfileClientMemory(clubProfileRef)
    val clubProfileClient             = testClubProfileClient()
    val clubProfileClientMemoryCached =
      ClubProfileClientMemory.cachedInstance(appConfig.clubProfileClient, clubProfileClient, clubProfileClientMemory)

    val clubPlayersClientMemory       = testClubPlayersClientMemory(clubPlayersRef)
    val clubPlayersClient             = testClubPlayersClient()
    val clubPlayersClientMemoryCached =
      ClubPlayersClientMemory.cachedInstance(appConfig.clubPlayersClient, clubPlayersClient, clubPlayersClientMemory)

    val clubSearchClient = testClubSearchClient()

    override val service = ClubService.impl(
      clubProfileClientMemoryCached,
      clubPlayersClientMemoryCached,
      clubSearchClient
    )

  }

  private def testClubProfileClient(): ClubProfileClient[IO] = (id: ClubId) =>
    IO.pure(
      parser.parse(jsonString(s"clubProfile/${id.value}.json")) match {
        case Right(json)          => Right(json)
        case Left(parsingFailure) => Left(ClubProfileClientException(parsingFailure.getMessage()))
      }
    )

  private def testClubSearchClient(): ClubSearchClient[IO] =
    (clubName: String) =>
      IO.pure(
        io.circe
          .parser
          .decode[ClubSearchResponse](jsonString("clubSearch/testResponseClubSearch.json"))
          .toOption
          .get
          .result
      )

  private def testClubPlayersClient(): ClubPlayersClient[IO] = (id: ClubId) =>
    IO.pure(
      parser.parse(jsonString(s"clubPlayers/${id.value}.json")) match {
        case Right(json)          => Right(json)
        case Left(parsingFailure) => Left(ClubPlayersClientException(parsingFailure.getMessage()))
      }
    )

  private def testClubProfileClientMemory(
    ref: Ref[IO, Map[ClubId, Json]]
  ): ClubProfileClientMemory[IO] =
    new ClubProfileClientMemory[IO] {

      override def save(clubId: ClubId)(clubJson: Json): IO[ErrorOr[Unit]] = (for {
        json <- EitherT.right[GameException](ref.update(_ + (clubId -> clubJson)))
      } yield json).value

      override def getById(clubId: ClubId): IO[ErrorOr[Json]] = ref
        .get
        .map(_.get(clubId) match {
          case Some(clubJson) => Right(clubJson)
          case None           => Left(ClubProfileJsonNotFoundInMemoryCacheException(clubId))
        })

    }

  private def testClubPlayersClientMemory(
    ref: Ref[IO, Map[ClubId, Json]]
  ): ClubPlayersClientMemory[IO] =
    new ClubPlayersClientMemory[IO] {

      override def save(clubId: ClubId)(clubJson: Json): IO[ErrorOr[Unit]] = (for {
        json <- EitherT.right[GameException](ref.update(_ + (clubId -> clubJson)))
      } yield json).value

      override def getById(clubId: ClubId): IO[ErrorOr[Json]] = ref
        .get
        .map(_.get(clubId) match {
          case Some(clubJson) => Right(clubJson)
          case None           => Left(ClubPlayersJsonNotFoundInMemoryCacheException(clubId))
        })

    }

}
