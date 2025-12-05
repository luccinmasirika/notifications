package com.irembo.notifications.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Hybrid Cache Configuration.
 *
 * Supports two cache implementations:
 * - Redis: Distributed cache for production (multi-instance deployment)
 * - Caffeine: Local in-memory cache for development (single instance)
 *
 * The cache type is controlled by the environment variable CACHE_TYPE or
 * Spring property cache.type (default: redis).
 *
 * Benefits:
 * - Redis: Shared cache across multiple instances, no inconsistencies
 * - Caffeine: Faster for dev, no external dependencies
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    @Value("${cache.type:redis}")
    private String cacheType;

    /**
     * Primary cache manager bean.
     * Chooses between Redis and Caffeine based on cache.type property.
     */
    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        if ("caffeine".equalsIgnoreCase(cacheType)) {
            logger.info("Using Caffeine (local) cache manager for development");
            return caffeineCacheManager();
        }

        logger.info("Using Redis (distributed) cache manager for production");
        return redisCacheManager(redisConnectionFactory);
    }

    /**
     * Redis cache manager (recommended for production).
     * Provides distributed caching across multiple application instances.
     *
     * Configuration:
     * - TTL: 24 hours for apiKeyValidation (for 100M+ users scale)
     * - TTL: 5 minutes for other caches
     * - Serialization: JSON (GenericJackson2JsonRedisSerializer)
     * - Transaction aware: Changes are synchronized with database transactions
     * 
     * Performance optimization for 100M+ users:
     * - Long TTL on API key validation cache (24h) ensures >99.9% hit rate
     * - API keys change rarely, so long TTL is safe
     * - Reduces database load dramatically
     */
    private CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // Default config: 5 minutes for most caches
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                )
                .disableCachingNullValues();

        // Special config for API key validation: 24 hours TTL
        // This is critical for 100M+ users scale
        RedisCacheConfiguration apiKeyValidationConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()
                        )
                )
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("apiKeyValidation", apiKeyValidationConfig)
                .transactionAware()
                .build();
    }

    /**
     * Caffeine cache manager (local in-memory cache for development).
     *
     * Configuration:
     * - Max size: 500 entries
     * - TTL: 5 minutes after write
     * - Stats: Enabled for monitoring (hit/miss ratio)
     *
     * Caches:
     * - clientConfigs: Client limit configurations
     * - systemLimits: System-wide rate limits
     * - apiKeyValidation: API key validation results (CRITICAL for performance)
     *
     * Note: Not suitable for multi-instance deployments as each instance
     * has its own cache, leading to potential inconsistencies.
     */
    private CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("clientConfigs", "systemLimits", "apiKeyValidation");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
}
