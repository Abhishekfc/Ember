package com.ember.backend.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

/** Read-through cache for the two hottest, most repeatedly-fetched-unchanged queries in the
 * app: a user's photo feed and their friends list, both hit on every Home/Friends screen open.
 * A short TTL (rather than relying solely on the explicit evictions in PhotoService/FriendService)
 * is the safety net for any write path that doesn't evict — e.g. a display name change — so
 * staleness self-heals within seconds even where eviction coverage isn't exhaustive. */
@Configuration
@EnableCaching
class CacheConfig(private val objectMapper: ObjectMapper) {

    @Bean
    fun cacheManager(connectionFactory: RedisConnectionFactory): RedisCacheManager {
        // A copy, not the shared app-wide mapper — default typing adds `@class` hints that
        // deserialization back into List<FeedItem>/List<FriendSummary> needs, but those hints
        // have no business leaking into the actual HTTP API's JSON responses.
        val redisMapper = objectMapper.copy().apply {
            activateDefaultTyping(polymorphicTypeValidator, ObjectMapper.DefaultTyping.NON_FINAL)
        }

        val cacheConfiguration = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofSeconds(30))
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(GenericJackson2JsonRedisSerializer(redisMapper))
            )

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(cacheConfiguration)
            .build()
    }
}
