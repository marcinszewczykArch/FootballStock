package cache

import cache.CacheUtils._
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

class CacheSpec extends CatsEffectSuite {

  private type TestState = List[IO[V]]
  private type F[A] = Kleisli[IO, Ref[IO, TestState], A]

  private val LookupFunc: K => F[V] = _ =>
    Kleisli { ref =>
      ref
        .modify {
          case head :: tail => (tail, head)
          case Nil          => fail("State is empty!")
        }
        .flatMap(identity)
    }

  private def successLookup(response: V): IO[V] = IO.pure(response)
  private val failedLookup: IO[V] = IO.raiseError(new RuntimeException("Failed test lookup") with NoStackTrace)

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
    Cache.instance[F, K, V]("TEST")(LookupFunc)(TTL, FailedFetchTTL)
  }

  test("should return cached value") {
    run(successLookup(key1resp1) :: successLookup(key2resp1) :: Nil) {
      val cache = testCacheInstance
      for {
        cachedVal11 <- cache.get(key1)
        cachedVal21 <- cache.get(key2)
        cachedVal12 <- cache.get(key1)
        cachedVal22 <- cache.get(key2)
      } yield {
        assert(cachedVal11 == key1resp1)
        assert(cachedVal12 == key1resp1)

        assert(cachedVal21 == key2resp1)
        assert(cachedVal22 == key2resp1)
      }
    }
  }

  test("should refresh cache after expiration") {
    run(successLookup(key1resp1) :: successLookup(key1resp2) :: successLookup(key1resp3) :: Nil) {
      val cache = testCacheInstance
      for {
        cachedVal1 <- cache.get(key1)
        _          <- sleep(TTL)
        cachedVal2 <- cache.get(key1)
        _          <- sleep(TTL)
        cachedVal3 <- cache.get(key1)
      } yield {
        assert(cachedVal1 == key1resp1)
        assert(cachedVal2 == key1resp2)
        assert(cachedVal3 == key1resp3)
      }
    }
  }

  test("should refresh cache after expiration with failed TTL when cache lookup failed") {
    run(successLookup(key1resp1) :: failedLookup :: successLookup(key1resp3) :: Nil) {
      val cache = testCacheInstance
      for {
        cachedVal1 <- cache.get(key1)
        _          <- sleep(TTL)
        cachedVal2 <- cache.get(key1)
        _          <- sleep(FailedFetchTTL)
        cachedVal3 <- cache.get(key1)
      } yield {
        assert(cachedVal1 == key1resp1)
        assert(cachedVal2 == key1resp1)
        assert(cachedVal3 == key1resp3)
      }
    }
  }

  test("should return error when cache cannot be initialized and recover when available") {
    run(failedLookup :: successLookup(key1resp1) :: Nil) {
      val cache = testCacheInstance
      for {
        cachedVal1 <- cache.get(key1).attempt
        cachedVal2 <- cache.get(key1)
      } yield {
        assert(cachedVal1.isLeft)
        assert(cachedVal2 == key1resp1)
      }
    }
  }
}
