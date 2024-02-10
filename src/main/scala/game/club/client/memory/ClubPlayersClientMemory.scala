package game.club.client.memory

import cats.Applicative
import cats.data.EitherT
import cats.effect._
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.toFlatMapOps
import cats.implicits.toFunctorOps
import cats.syntax.all._
import config.AppConfig.PlayerProfileClientConfig
import game.club.service.domain.ClubId
import game.errors.GameException
import game.errors.GameException.DynamoReaderException
import game.errors.GameException.JsonParsingFailure
import game.errors.GameException.PlayerJsonNotFoundInMemoryCacheException
import game.player.client.PlayerProfileClient
import game.player.service.domain.PlayerId
import io.circe.Json
import io.circe.parser
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import utils.Cache

trait ClubPlayersClientMemory[F[_]] {

  def getById(clubId: ClubId): F[Either[GameException, Json]]
  def getAll(): F[Map[ClubId, Json]]
  def save(clubId: ClubId)(clubJson: Json): F[Either[GameException, Unit]]

}

object ClubPlayersClientMemory {

//  def impl[F[_]: Sync: LoggerFactory](scanamo: Scanamo): ClubClientMemory[F] =
//    new ClubClientMemory[F] {
//      import org.scanamo._
//      import org.scanamo.generic.auto._
//      import org.scanamo.syntax._
//
//      implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[ClubClientMemory[F]].getName)
//
//      private val SOURCE_TRANSFERMARKT = "Transfermarkt"
//      private val table                = Table[PlayerProfileTable]("Club")
//      private case class PlayerProfileTable(source: String, playerId: Int, json: String)
//
//      override def getById(clubId: Any): F[Either[GameException, Json]]              = ???
//      override def getAll(): F[Map[Any, Json]]                                       = ???
//      override def save(clubId: Any)(clubJson: Json): F[Either[GameException, Unit]] = ???
//    }

}
