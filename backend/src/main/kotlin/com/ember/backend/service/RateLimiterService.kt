package com.ember.backend.service

import com.ember.backend.exception.RateLimitExceededException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

/** Simple fixed-window rate limiter backed by Redis (already a dependency for caching), used to
 * throttle `/auth/login` and `/auth/register` per client IP — neither had any brute-force or
 * mass-registration protection at all before this. INCR + an EXPIRE set only on the first
 * increment of each window is the standard fixed-window counter pattern: cheap (two Redis ops
 * per check), and correct enough for abuse-throttling even though it isn't a perfectly smooth
 * sliding window. */
@Service
class RateLimiterService(private val redisTemplate: StringRedisTemplate) {

    /** Throws [RateLimitExceededException] once [key] has been checked more than [maxAttempts]
     * times within [window]. */
    fun checkLimit(key: String, maxAttempts: Int, window: Duration) {
        val redisKey = "ratelimit:$key"
        val count = redisTemplate.opsForValue().increment(redisKey) ?: 1L
        if (count == 1L) {
            redisTemplate.expire(redisKey, window)
        }
        if (count > maxAttempts) {
            throw RateLimitExceededException()
        }
    }
}
