package game.modules.event.memory

import cats.effect._
import cats.implicits.catsSyntaxApplicativeId
import cats.implicits.catsSyntaxApplyOps
import cats.implicits.toFunctorOps
import cats.implicits.toTraverseOps
import game.GameException
import GameException.DynamoReaderException
import GameException.JsonDecodingException
import GameException.JsonParsingFailure
import GameException.UserNotFoundException
import game.modules.event.Event.BuyPlayerEvent
import game.modules.event.Event.InitializeGameEvent
import game.modules.event.Event.PlayersUpdateEvent
import game.modules.event.Event.SellPlayerEvent
import game.modules.event.Event
import game.modules.state.domain.User
import io.circe.parser
import io.circe.syntax.EncoderOps
import org.scanamo.Scanamo
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger
import utils.Type.ErrorOr

import java.util.UUID

trait EventMemory[F[_]] {

  def sendEvent(event: Event): F[Unit]
  def getEventsForUser(user: User): F[ErrorOr[List[Event]]]
//todo: get events for user and player
}

object EventMemory {

  def impl[F[_]: Sync: LoggerFactory](scanamo: Scanamo): EventMemory[F] =
    new EventMemory[F] {

      import org.scanamo._
      import org.scanamo.generic.auto._
      import org.scanamo.syntax._

      implicit val log: SelfAwareStructuredLogger[F] =
        LoggerFactory.getLoggerFromName[F](classOf[EventMemory[F]].getName)

      private val table = Table[EventTable]("Event")
      private case class EventTable(user: String, eventId: String = UUID.randomUUID().toString, eventName: String, json: String)
//todo: add playerId to Table
      override def sendEvent(event: Event): F[Unit] =
        log.debug(s"sending new event for ${event.getUser} to dynamoDb: $event") *> scanamo
          .exec(
            table.put(
              EventTable(
                user = event.getUser.value,
                eventName = event.getEventName,
                json = event.asJson.toString
              )
            )
          )
          .pure

      override def getEventsForUser(user: User): F[ErrorOr[List[Event]]] =
        log.debug(s"getting events for $user from dynamoDb") *>
          (scanamo
            .exec(table.query("user" === user.value))
            .sequence
            .left
            .map(err => DynamoReaderException(err.toString)) match {
            case Left(err) =>
              Left[GameException, List[Event]](DynamoReaderException(s"Result for $user could not be found in memory: $err"))

            case Right(events) =>
              events
                .map(record => (record.eventName, record.json))
                .map { case (eventName, jsonString) => toEvent(eventName, jsonString) }
                .sequence

          }).pure

    }

  private def toEvent(eventName: String, jsonString: String): ErrorOr[Event] =
    parser.parse(jsonString) match {
      case Left(parsingFailure) => Left[GameException, Event](JsonParsingFailure(parsingFailure.getMessage()))
      case Right(json)          =>
        json.as[Event] match {
          case Left(decodingFailure) => Left[GameException, Event](JsonDecodingException(decodingFailure.getMessage()))
          case Right(event)          => Right[GameException, Event](event)
        }
    }

}
