package game.club.service

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.{catsSyntaxApplicativeError, toFlatMapOps, toFunctorOps}
import game.GameException
import game.club.client.ClubSearchClient
import game.club.client.memory.{ClubPlayersClientMemory, ClubProfileClientMemory}
import game.club.service.ClubMapper._
import game.club.service.domain.{ClubId, ClubPlayers, ClubProfile, ClubSimple}
import GameException.ClubSearchByNameException
import org.typelevel.log4cats.{LoggerFactory, SelfAwareStructuredLogger}
import utils.Type.ErrorOr

trait ClubService[F[_]] {
  def searchByName(clubName: String): F[ErrorOr[List[ClubSimple]]]
  def getClubProfileById(id: ClubId): F[ErrorOr[ClubProfile]]
  def getClubPlayersById(id: ClubId): F[ErrorOr[ClubPlayers]]
}

object ClubService {

  def impl[F[_]: Sync: LoggerFactory](
    clubProfileClientMemory: ClubProfileClientMemory[F],
    clubPlayersClientMemory: ClubPlayersClientMemory[F],
    clubSearchClient: ClubSearchClient[F]
  ) = new ClubService[F] {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[ClubService[F]].getName)

    override def searchByName(clubName: String): F[ErrorOr[List[ClubSimple]]] =
      clubSearchClient
        .searchByName(clubName)
        .map(_.map(fetchedClubSimpleToClubSimple))
        .attempt
        .flatMap {
          case Right(clubsList) =>
            Applicative[F].pure(Right(clubsList))
          case Left(err)        =>
            log
              .error(s"Clubs search for '$clubName' failed: ${err.getMessage}")
              .as(
                Left(ClubSearchByNameException(clubName, err.getMessage))
              )
        }

    override def getClubProfileById(id: ClubId): F[ErrorOr[ClubProfile]] = (for {
      json               <- EitherT(clubProfileClientMemory.getById(id))
      fetchedClubProfile <- EitherT.fromEither(jsonToFetchedClubProfile(json))
      clubProfile = fetchedClubToClub(fetchedClubProfile)
    } yield clubProfile).value

    override def getClubPlayersById(id: ClubId): F[ErrorOr[ClubPlayers]] = (for {
      json               <- EitherT(clubPlayersClientMemory.getById(id))
      fetchedClubPlayers <- EitherT.fromEither(jsonToFetchedClubPlayers(json))
      clubPlayers = fetchedClubPlayersToClubPlayers(fetchedClubPlayers)
    } yield clubPlayers).value

  }

}
