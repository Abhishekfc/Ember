package com.ember.backend.service

import com.ember.backend.exception.RateLimitExceededException
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

/** Upper bound on how many distinct keys are tracked at once, so a flood of requests from rotating
 * IPs can't grow this without limit. Past it, Caffeine evicts least-recently-used entries — which
 * resets that key's counter early, the same thing Redis did under memory pressure. Sized far above
 * any realistic number of genuine clients, so eviction only ever happens under actual abuse. */
private const val MAX_TRACKED_KEYS = 100_000L

/** Cleanup horizon for idle keys. Only needs to exceed the longest window any caller passes
 * (currently 24h, in UserSafetyController's report limit) — the window logic itself is enforced by
 * the timestamp on each entry, not by this, so this is purely about not retaining dead keys. */
private val IDLE_RETENTION: Duration = Duration.ofHours(25)

/** One fixed window: when it opened, and how many attempts have landed in it. Immutable, so it can
 * only ever be replaced wholesale inside the atomic compute below. */
private data class AttemptWindow(val startedAt: Instant, val attempts: Int)

/** Simple fixed-window rate limiter, used to throttle `/auth/login`, `/auth/register`, the
 * availability lookups, friend requests and user reports — none of which had any brute-force or
 * mass-abuse protection before this existed.
 *
 * In-process (Caffeine) rather than Redis: see CacheConfig for the full reasoning behind dropping
 * Redis entirely. The behaviour here is deliberately identical to the Redis version it replaces,
 * including that incrementing within a window does **not** extend that window — a fixed window
 * starts when its first attempt lands and ends [window] later, exactly as `INCR` plus an `EXPIRE`
 * set only on the first increment behaved.
 *
 * The one real trade: counters are per-instance and reset on restart. With a single instance that
 * is equivalent to before; if this ever runs on more than one replica, each would enforce the limit
 * separately and a shared store has to come back.
 */
@Service
class RateLimiterService {

    private val windows: Cache<String, AttemptWindow> = Caffeine.newBuilder()
        .maximumSize(MAX_TRACKED_KEYS)
        .expireAfterWrite(IDLE_RETENTION)
        .build()

    /** Throws [RateLimitExceededException] once [key] has been checked more than [maxAttempts]
     * times within [window]. */
    fun checkLimit(key: String, maxAttempts: Int, window: Duration) {
        val now = Instant.now()
        // compute() is atomic per key in Caffeine, so concurrent requests for the same key can't
        // interleave a read and a write and lose an increment — which is what made the Redis
        // version's single INCR safe, and has to stay true here.
        val updated = windows.asMap().compute(key) { _, existing ->
            if (existing == null || Duration.between(existing.startedAt, now) >= window) {
                AttemptWindow(startedAt = now, attempts = 1)
            } else {
                existing.copy(attempts = existing.attempts + 1)
            }
        }!!

        if (updated.attempts > maxAttempts) {
            throw RateLimitExceededException()
        }
    }
}
