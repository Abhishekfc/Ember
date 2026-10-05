package com.emigo.app.data

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class SingleFlightTest {

    // Long enough for a second caller to join the in-flight work, short enough to keep tests fast.
    private val joinWindowMillis = 150L

    @Test
    fun concurrentCallsForTheSameKeyRunTheWorkOnce() = runBlocking {
        withTimeout(5_000) {
            val flight = SingleFlight<String, Int>()
            val runs = AtomicInteger()
            val gate = CompletableDeferred<Unit>()
            val work: suspend () -> Int = {
                runs.incrementAndGet()
                gate.await()
                42
            }

            val first = async { flight.run("feed", work) }
            val second = async { flight.run("feed", work) }
            delay(joinWindowMillis)
            gate.complete(Unit)

            assertEquals(42, first.await())
            assertEquals(42, second.await())
            assertEquals(1, runs.get())
        }
    }

    @Test
    fun differentKeysRunSeparately() = runBlocking {
        withTimeout(5_000) {
            val flight = SingleFlight<String, String>()
            val runs = AtomicInteger()
            val gate = CompletableDeferred<Unit>()

            val a = async { flight.run("a") { runs.incrementAndGet(); gate.await(); "a" } }
            val b = async { flight.run("b") { runs.incrementAndGet(); gate.await(); "b" } }
            delay(joinWindowMillis)
            gate.complete(Unit)

            assertEquals("a", a.await())
            assertEquals("b", b.await())
            assertEquals(2, runs.get())
        }
    }

    @Test
    fun aCallAfterCompletionRunsTheWorkAgain() = runBlocking {
        withTimeout(5_000) {
            val flight = SingleFlight<String, Int>()
            val runs = AtomicInteger()

            flight.run("feed") { runs.incrementAndGet() }
            flight.run("feed") { runs.incrementAndGet() }

            assertEquals(2, runs.get())
        }
    }

    @Test
    fun cancellingOneCallerDoesNotCancelTheSharedWork() = runBlocking {
        withTimeout(5_000) {
            val flight = SingleFlight<String, Int>()
            val runs = AtomicInteger()
            val gate = CompletableDeferred<Unit>()
            val work: suspend () -> Int = {
                runs.incrementAndGet()
                gate.await()
                7
            }

            val cancelled = launch { flight.run("feed", work) }
            val survivor = async { flight.run("feed", work) }
            delay(joinWindowMillis)
            cancelled.cancel()
            gate.complete(Unit)

            assertEquals(7, survivor.await())
            assertEquals(1, runs.get())
        }
    }
}
