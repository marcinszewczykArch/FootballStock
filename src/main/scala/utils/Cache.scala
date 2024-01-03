package utils

import cats.Applicative
import cats.MonadThrow
import cats.effect.Clock
import cats.effect.Sync
import cats.effect.kernel.Ref
import cats.effect.std.MapRef
import cats.syntax.all._
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.SelfAwareStructuredLogger

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.duration.FiniteDuration

trait Cache[F[_], K, V] {
  def get(key: K): F[V]
  def getBatch(keys: List[K]): F[Map[K, V]]
  def getSize: Int
}

object Cache {

  def instance[F[_]: Sync: LoggerFactory, K, V](
    cacheName: String
  )(
    lookup: K => F[V],
    maybeBatchLookup: Option[List[K] => F[Map[K, V]]] = None
  )(
    ttl: FiniteDuration,
    failedFetchTtl: FiniteDuration
  ): Cache[F, K, V] = {
    implicit val log: SelfAwareStructuredLogger[F] = LoggerFactory.getLoggerFromName[F](classOf[Cache[F, K, V]].getName + "." + cacheName)

    val underlying: ConcurrentHashMap[K, Entry[V]] = new ConcurrentHashMap()
    val cacheRef: MapRef[F, K, Option[Entry[V]]] = MapRef.fromConcurrentHashMap[F, K, Entry[V]](underlying)

    new Cache[F, K, V] {
      override def getSize: Int = underlying.size()

      override def get(key: K): F[V] = for {
        now   <- Clock[F].monotonic
        value <- cacheRef(key).access.flatMap { case (entry: Option[Entry[V]], setter) =>
                   entry match {
                     case Some(Entry(value, deadline)) if now < deadline =>
                       value.pure[F]
                     case Some(Entry(value, _))                          =>
                       lookup(key)
                         .attempt
                         .flatMap {
                           case Left(err) =>
                             log.error(err)(s"Failed to refresh cache for key: $key") *>
                               setter(Some(Entry(value, now + failedFetchTtl))).as(value)

                           case Right(result) =>
                             setter(Some(Entry(result, now + ttl))).as(result)
                         }
                     case None                                           =>
                       lookup(key)
                         .attemptTap {
                           case Left(err)     => log.error(err)(s"Failed to initialize cache for key: $key")
                           case Right(result) => setter(Some(Entry(result, now + ttl))).void
                         }
                   }

                 }
      } yield value

      override def getBatch(keys: List[K]): F[Map[K, V]] = maybeBatchLookup match {
        case None              => getEachSeparately(keys)
        case Some(batchLookup) =>
          for {
            implicit0(now: FiniteDuration)           <- Clock[F].monotonic
            state                                    <- Ref.of[F, BatchState[K, V]](BatchState.empty)
            _                                        <- keys.traverse { key =>
                                                          cacheRef(key).access.flatMap { case (entry, _) =>
                                                            entry match {
                                                              case Some(Entry(value, deadline)) if now < deadline =>
                                                                state.update(state => state.copy(fromCache = state.fromCache + (key -> value)))
                                                              case Some(_)                                        =>
                                                                state.update(state => state.copy(expired = key :: state.expired))
                                                              case None                                           =>
                                                                state.update(state => state.copy(notFound = key :: state.notFound))
                                                            }
                                                          }
                                                        }
            BatchState(notFound, expired, fromCache) <- state.get
            fromLookup                               <- notFound ++ expired match {
                                                          case Nil      => Map.empty[K, V].pure[F]
                                                          case toLookup =>
                                                            batchLookup(toLookup).attempt.flatMap {
                                                              case Right(result) => addToCacheAndReturn(result)
                                                              case Left(err)     => dealWithLookupError(err)(notFound, expired)
                                                            }
                                                        }
          } yield fromLookup ++ fromCache
      }

      private def addToCacheAndReturn(result: Map[K, V])(implicit now: FiniteDuration): F[Map[K, V]] =
        result
          .toList
          .traverse { case (key, value) =>
            cacheRef(key).access.flatMap { case (_, setter) => setter(Some(Entry(value, now + ttl))) }
          }
          .as(result)

      private def dealWithLookupError(
        err: Throwable
      )(
        notFound: List[K],
        expired: List[K]
      )(
        implicit
        log: Logger[F],
        now: FiniteDuration
      ): F[Map[K, V]] = {
        notFound match {
          case Nil =>
            expired
              .traverse { key =>
                cacheRef(key).access.flatMap {
                  case (Some(Entry(value, _)), setter) =>
                    setter(Some(Entry(value, now + failedFetchTtl))).as(key -> value)
                  case _                               => MonadThrow[F].raiseError[(K, V)](LookupException(Nil, expired))
                }
              }
              .map(_.toMap)
          case _   =>
            expired
              .traverse { key =>
                cacheRef(key).access.flatMap {
                  case (Some(Entry(value, _)), setter) => setter(Some(Entry(value, now + failedFetchTtl))).as(())
                  case _                               => Applicative[F].unit
                }
              } *> MonadThrow[F].raiseError[Map[K, V]](LookupException(notFound, expired))

        }
      } <* log.error(err)(s"Failed to initialize cache for keys: $notFound").whenA(notFound.nonEmpty) <*
        log.error(err)(s"Failed to refresh cache for keys: $expired").whenA(expired.nonEmpty)

      private def getEachSeparately(keys: List[K]) =
        keys
          .map(k => (k, this.get(k)))
          .traverse { case (k, v) => v.map(k -> _) }
          .map(_.toMap)

    }
  }

  private final case class Entry[T](value: T, deadline: FiniteDuration)

  private final case class LookupException[K](notFound: List[K], expired: List[K])
    extends RuntimeException(s"Failed to initialize cache for keys: $notFound and to refresh cache for keys: $expired")

  private final case class BatchState[K, V](notFound: List[K], expired: List[K], fromCache: Map[K, V])

  private object BatchState {
    def empty[K, V] = new BatchState[K, V](Nil, Nil, Map.empty)
  }

}
