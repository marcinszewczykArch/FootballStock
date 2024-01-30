package cache

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

object CacheUtils {

  type K = String
  type V = String

  val TTL: FiniteDuration            = 1.day
  val FailedFetchTTL: FiniteDuration = 1.hour

  val key1      = "KEY-1"
  val key1resp1 = "KEY-1-RESP-1"
  val key1resp2 = "KEY-1-RESP-2"
  val key1resp3 = "KEY-1-RESP-3"
  val key2      = "KEY-2"
  val key2resp1 = "KEY-2-RESP-1"
  val key2resp2 = "KEY-2-RESP-2"
  val key2resp3 = "KEY-2-RESP-3"
  val key3      = "KEY-3"
  val key3resp1 = "KEY-3-RESP-1"
  val key3resp2 = "KEY-3-RESP-2"

}
