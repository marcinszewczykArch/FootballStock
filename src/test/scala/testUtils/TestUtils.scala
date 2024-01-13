package testUtils

import cats.data.EitherT
import cats.effect.IO
import cats.effect.Ref
import game.errors.GameException
import game.errors.GameException.PlayerJsonNotFoundInMemoryException
import game.errors.GameException.PlayerProfileClientException
import game.errors.GameException.UserNotFoundException
import game.events.Event
import game.events.memory.EventMemory
import game.gameState.domain.User
import game.gameState.domain.UserGameState
import game.gameState.memory.UserGameStateMemory
import game.player.client.PlayerProfileClient
import game.player.client.PlayerSearchClient
import game.player.client.domain.FetchedPlayerSimple
import game.player.client.domain.PlayerSearchResponse
import game.player.client.memory.PlayerProfileClientMemory
import game.player.service.domain.PlayerId
import io.circe.Json
import io.circe.parser
import utils.JsonParser.jsonString
import utils.TimeProvider

import java.time.Instant

object TestUtils {

  def testPlayerProfileClient(): IO[PlayerProfileClient[IO]] = IO.pure((id: PlayerId) =>
    IO.pure(
      parser.parse(jsonString(s"players/${id.value}.json")) match {
        case Right(json)          => Right(json)
        case Left(parsingFailure) => Left(PlayerProfileClientException(parsingFailure.getMessage()))
      }
    )
  )

  def testPlayerSearchClient(): IO[PlayerSearchClient[IO]] = IO.pure(
    new PlayerSearchClient[IO] {

      def searchByName(playerName: String): IO[List[FetchedPlayerSimple]] =
        IO.pure(
          io.circe
            .parser
            .decode[PlayerSearchResponse](jsonString("playerSearch/testResponsePlayerSearch.json"))
            .toOption
            .get
            .result
        )

    }
  )

  def testPlayerProfileClientMemory(
    ref: Ref[IO, Map[PlayerId, Json]]
  ): IO[PlayerProfileClientMemory[IO]] = IO.pure(
    new PlayerProfileClientMemory[IO] {

      override def save(playerId: PlayerId)(playerJson: Json): IO[Either[GameException, Unit]] = (for {
        json <- EitherT.right[GameException](ref.update(_ + (playerId -> playerJson)))
      } yield json).value

      override def getById(playerId: PlayerId): IO[Either[GameException, Json]] = ref
        .get
        .map(_.get(playerId) match {
          case Some(playerJson) => Right(playerJson)
          case None             => Left(PlayerJsonNotFoundInMemoryException(playerId))
        })

      override def getAll(): IO[Map[PlayerId, Json]] = ref.get

    }
  )

  def testUserGameStateMemory(
    ref: Ref[IO, Map[User, UserGameState]]
  ): IO[UserGameStateMemory[IO]] = IO.pure(
    new UserGameStateMemory[IO] {

      def save(user: User)(newUserState: UserGameState): IO[Either[GameException, Unit]] = (for {
        _ <- EitherT.right[GameException](ref.update(_ + (user -> newUserState)))
      } yield ()).value

      def update(user: User)(newUserState: UserGameState)(versionNumber: Instant): IO[Either[GameException, Unit]] =
        save(user)(newUserState)

      def getByUser(user: User): IO[Either[GameException, UserGameState]] = ref
        .get
        .map(_.get(user) match {
          case Some(userStats) => Right(userStats)
          case None            => Left(UserNotFoundException(user))
        })

      def getAll(): IO[Map[User, UserGameState]] = ref.get

    }
  )

  def testEventMemory(ref: Ref[IO, List[Event]]) = IO.pure(
    new EventMemory[IO] {
      override def sendEvent(event: Event): IO[Unit] = ref.update(_ :+ event)

      override def getEventsForUser(user: User): IO[Either[GameException, List[Event]]] = ref
        .get
        .map(_.filter(_.getUser == user) match {
          case Nil                     => Left(UserNotFoundException(user))
          case userEvents: List[Event] => Right(userEvents)
        })

    }
  )

  def testTimeProvider(now: Instant) = IO.pure(new TimeProvider[IO] {
    override def getCurrentTimestamp: Instant = now
  })

}
