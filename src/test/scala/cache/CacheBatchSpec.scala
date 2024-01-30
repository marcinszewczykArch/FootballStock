package cache

import cache.CacheUtils._
import cats.MonadThrow
import cats.data.Kleisli
import cats.effect._
import cats.effect.testkit.TestControl
import cats.syntax.all._
import munit._
import org.typelevel.log4cats.LoggerFactory
import org.typelevel.log4cats.noop.NoOpFactory
import utils.Cache

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NoStackTrace

class CacheBatchSpec extends CatsEffectSuite {

  private type TestState = List[IO[Map[K, V]]]
  private type F[A] = Kleisli[IO, Ref[IO, TestState], A]

  private val LookupFunc: List[K] => F[Map[K, V]] = _ =>
    Kleisli { ref =>
      ref
        .modify {
          case head :: tail => (tail, head)
          case Nil          => fail("State is empty!")
        }
        .flatMap(_.map(_.toMap))
    }

  private val failedLookup: IO[Map[K, V]] = IO.raiseError(new RuntimeException("Failed test lookup") with NoStackTrace)

  private def successLookup(response: Map[K, V]): IO[Map[K, V]] = IO.pure(response)

  private def sleep(duration: FiniteDuration): F[Unit] = Kleisli.liftF(IO.sleep(duration))

  private def run(testState: TestState)(testBody: F[Unit]): IO[Unit] = TestControl.executeEmbed {
    for {
      refState   <- Ref[IO].of(testState)
      _          <- testBody.run(refState)
      finalState <- refState.get
    } yield assert(finalState.isEmpty)
  }

  private def testCacheInstance: Cache[F, K, V] = {
    implicit val loggerFactory: LoggerFactory[F] = NoOpFactory.impl[F]
    val LookupFuncStub: K => F[V] = _ =>
      MonadThrow[F].raiseError[V](new RuntimeException("This is stub of LookupFunc and it should not be used here") with NoStackTrace)
    Cache.instance[F, K, V]("TEST")(LookupFuncStub, LookupFunc.some)(TTL, FailedFetchTTL)
  }

  test("should refresh cache after expiration") {
    run(
      successLookup(Map(key1 -> key1resp1, key2 -> key2resp1)) ::
        successLookup(Map(key1 -> key1resp2, key2 -> key2resp2)) ::
        successLookup(Map(key1 -> key1resp3, key2 -> key2resp3)) ::
        Nil
    ) {
      val cache = testCacheInstance
      for {
        cachedVal1 <- cache.getBatch(List(key1, key2))
        _          <- sleep(TTL)
        cachedVal2 <- cache.getBatch(List(key1, key2))
        _          <- sleep(TTL)
        cachedVal3 <- cache.getBatch(List(key1, key2))
      } yield {
        assertEquals(cachedVal1, Map(key1 -> key1resp1, key2 -> key2resp1))
        assertEquals(cachedVal2, Map(key1 -> key1resp2, key2 -> key2resp2))
        assertEquals(cachedVal3, Map(key1 -> key1resp3, key2 -> key2resp3))
      }
    }
  }

  test("should refresh cache only for expired keys") {
    run(
      successLookup(Map(key1 -> key1resp1, key2 -> key2resp1)) ::
        successLookup(Map(key3 -> key3resp1)) ::
        successLookup(Map(key1 -> key1resp2, key2 -> key2resp2)) ::
        Nil
    ) {
      val cache = testCacheInstance
      for {
        cachedVal1 <- cache.getBatch(List(key1, key2))
        _          <- sleep(TTL / 2)
        cachedVal2 <- cache.getBatch(List(key1, key2, key3))
        _          <- sleep(TTL / 2)
        cachedVal3 <- cache.getBatch(List(key1, key2, key3))
      } yield {
        assertEquals(cachedVal1, Map(key1 -> key1resp1, key2 -> key2resp1))
        assertEquals(cachedVal2, Map(key1 -> key1resp1, key2 -> key2resp1, key3 -> key3resp1))
        assertEquals(cachedVal3, Map(key1 -> key1resp2, key2 -> key2resp2, key3 -> key3resp1))
      }
    }
  }

  test("should refresh cache after expiration with failed TTL when cache lookup failed") {
    run(
      successLookup(Map(key1 -> key1resp1, key2 -> key2resp1, key3 -> key3resp1)) ::
        failedLookup ::
        successLookup(Map(key1 -> key1resp2, key2 -> key2resp2, key3 -> key3resp2)) ::
        Nil
    ) {
      val cache = testCacheInstance
      for {
        cachedVal1 <- cache.getBatch(List(key1, key2, key3))
        _          <- sleep(TTL)
        cachedVal2 <- cache.getBatch(List(key1, key2, key3))
        _          <- sleep(FailedFetchTTL)
        cachedVal3 <- cache.getBatch(List(key1, key2, key3))
      } yield {
        assertEquals(cachedVal1, Map(key1 -> key1resp1, key2 -> key2resp1, key3 -> key3resp1))
        assertEquals(cachedVal2, Map(key1 -> key1resp1, key2 -> key2resp1, key3 -> key3resp1))
        assertEquals(cachedVal3, Map(key1 -> key1resp2, key2 -> key2resp2, key3 -> key3resp2))
      }
    }
  }

  test("should return error when cache cannot be initialized and recover when available") {
    run(
      failedLookup ::
        successLookup(Map(key1 -> key1resp1, key2 -> key2resp1, key3 -> key3resp1)) ::
        Nil
    ) {
      val cache = testCacheInstance
      for {
        cachedVal1 <- cache.getBatch(List(key1, key2, key3)).attempt
        cachedVal2 <- cache.getBatch(List(key1, key2, key3))
      } yield {
        assert(cachedVal1.isLeft)
        assertEquals(cachedVal2, Map(key1 -> key1resp1, key2 -> key2resp1, key3 -> key3resp1))
      }
    }
  }
}
