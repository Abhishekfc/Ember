package com.ember.backend.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Read-through cache for the two hottest, most repeatedly-fetched-unchanged queries in the
 * app: a user's photo feed and their friends list, both hit on every Home/Friends screen open.
 * A short TTL (rather than relying solely on the explicit evictions in PhotoService/FriendService)
 * is the safety net for any write path that doesn't evict — e.g. a display name change — so
 * staleness self-heals within seconds even where eviction coverage isn't exhaustive.
 *
 * In-process (Caffeine) rather than Redis: with a 30-second TTL there is nothing worth keeping
 * across a restart, and one instance means nothing to share between. That removes a managed
 * service, its cost, and a whole class of failure — including, specifically, the recurring
 * `SerializationException` on empty-list entries that Redis's polymorphic type-wrapping caused
 * (documented at length in PROJECT_CONTEXT.md), since values are now stored as live objects and
 * never serialized at all. The callers' own `runCatching { ... }` guards around cache reads are
 * kept regardless — they cost nothing and stay correct if this is ever moved back off-process.
 *
 * The one real trade: both this and the rate limiter become per-instance. If this app ever runs
 * more than one replica, a shared cache has to come back.
 */
@Configuration
@EnableCaching
class CacheConfig {

    @Bean
    fun cacheManager(): CaffeineCacheManager = CaffeineCacheManager().apply {
        setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                // A hard ceiling so a large user base can't let these grow unbounded — entries
                // are per-user lists, and the least-recently-used are evicted past this point.
                // The 30s TTL means a miss costs one recompute, never correctness.
                .maximumSize(10_000),
        )
        // Matches Redis's own disableCachingNullValues: a null result must be recomputed rather
        // than remembered, since "no answer yet" isn't an answer worth keeping for 30 seconds.
        isAllowNullValues = false
    }
}
