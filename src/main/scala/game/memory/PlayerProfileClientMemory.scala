package game.memory

import cats.data.EitherT
import cats.effect._
import cats.implicits.toFunctorOps
import game.errors.GameException
import game.errors.GameException.PlayerJsonNotFoundInMemoryException
import game.player.service.domain.PlayerId
import io.circe.Json

trait PlayerProfileClientMemory[F[_]] {

  def savePlayerJson(playerId: PlayerId)(playerJson: Json): F[Either[GameException, Unit]]
  def getPlayerJson(playerId: PlayerId): F[Either[GameException, Json]]

}

object PlayerProfileClientMemory {

  def impl[F[_]](
    ref: Ref[F, Map[PlayerId, Json]]
  )(
    implicit F: Sync[F]
  ): PlayerProfileClientMemory[F] =
    new PlayerProfileClientMemory[F] {

      override def savePlayerJson(playerId: PlayerId)(playerJson: Json): F[Either[GameException, Unit]] = (for {
        _ <- EitherT.right[GameException](ref.update(_ + (playerId -> playerJson)))
      } yield ()).value

      override def getPlayerJson(playerId: PlayerId): F[Either[GameException, Json]] = ref
        .get
        .map(_.get(playerId) match {
          case Some(playerJson) => Right(playerJson)
          case None             => Left(PlayerJsonNotFoundInMemoryException(playerId.value))
        })

    }

}
