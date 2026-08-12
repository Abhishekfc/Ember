package com.ember.backend.service

import com.ember.backend.exception.RateLimitExceededException
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins the behaviour of the in-process rate limiter that replaced the Redis-backed one, so the
 * swap is verified rather than assumed. The contract being preserved: a fixed window that starts
 * on its first attempt, allows exactly [maxAttempts] within it, and is not extended by further
 * attempts landing inside it.
 */
class RateLimiterServiceTest {

    private val limiter = RateLimiterService()

    private val longWindow = Duration.ofHours(1)

    @Test
    fun `allows exactly the permitted number of attempts`() {
        repeat(5) { limiter.checkLimit("allow-5", maxAttempts = 5, window = longWindow) }
        // The 6th is the first one over the line.
        assertFailsWith<RateLimitExceededException> {
            limiter.checkLimit("allow-5", maxAttempts = 5, window = longWindow)
        }
    }

    @Test
    fun `stays blocked once over the limit`() {
        repeat(3) { limiter.checkLimit("stays-blocked", maxAttempts = 3, window = longWindow) }
        repeat(3) {
            assertFailsWith<RateLimitExceededException> {
                limiter.checkLimit("stays-blocked", maxAttempts = 3, window = longWindow)
            }
        }
    }

    @Test
    fun `keys are counted independently`() {
        // One client hitting its limit must not throttle a different one — this is the whole
        // reason the key carries the IP or user id.
        repeat(2) { limiter.checkLimit("ip-a", maxAttempts = 2, window = longWindow) }
        assertFailsWith<RateLimitExceededException> {
            limiter.checkLimit("ip-a", maxAttempts = 2, window = longWindow)
        }
        // ip-b is untouched and gets its own full allowance.
        repeat(2) { limiter.checkLimit("ip-b", maxAttempts = 2, window = longWindow) }
    }

    @Test
    fun `a fresh window is allowed once the old one has elapsed`() {
        val shortWindow = Duration.ofMillis(150)
        repeat(2) { limiter.checkLimit("expiring", maxAttempts = 2, window = shortWindow) }
        assertFailsWith<RateLimitExceededException> {
            limiter.checkLimit("expiring", maxAttempts = 2, window = shortWindow)
        }

        Thread.sleep(200)

        // Window elapsed — the counter resets rather than staying blocked forever.
        limiter.checkLimit("expiring", maxAttempts = 2, window = shortWindow)
    }

    @Test
    fun `attempts inside a window do not extend it`() {
        // The Redis version set an EXPIRE only on the first increment, so hammering a key could
        // never push its reset further out. That property has to survive the rewrite, or a
        // determined caller could keep themselves blocked (or, worse, keep a window alive
        // indefinitely) just by continuing to try.
        val window = Duration.ofMillis(200)
        limiter.checkLimit("no-extend", maxAttempts = 1, window = window)
        repeat(4) {
            Thread.sleep(40)
            runCatching { limiter.checkLimit("no-extend", maxAttempts = 1, window = window) }
        }
        Thread.sleep(60)
        // ~220ms since the window opened, despite four attempts in between: it must have reset.
        limiter.checkLimit("no-extend", maxAttempts = 1, window = window)
    }

    @Test
    fun `concurrent attempts on one key cannot lose an increment`() {
        // The Redis version relied on INCR being atomic. Here that job falls to Caffeine's
        // per-key compute(); if it weren't atomic, interleaved read-modify-write would undercount
        // and let more requests through than the limit allows.
        val threads = 16
        val attemptsPerThread = 50
        val total = threads * attemptsPerThread
        val allowed = 100

        val pool = Executors.newFixedThreadPool(threads)
        val startLine = CountDownLatch(1)
        val accepted = AtomicInteger(0)

        repeat(threads) {
            pool.submit {
                startLine.await()
                repeat(attemptsPerThread) {
                    runCatching {
                        limiter.checkLimit("concurrent", maxAttempts = allowed, window = longWindow)
                    }.onSuccess { accepted.incrementAndGet() }
                }
            }
        }
        startLine.countDown()
        pool.shutdown()
        check(pool.awaitTermination(30, TimeUnit.SECONDS)) { "rate limiter test timed out" }

        assertEquals(
            allowed,
            accepted.get(),
            "exactly $allowed of $total concurrent attempts should have been let through",
        )
    }
}
