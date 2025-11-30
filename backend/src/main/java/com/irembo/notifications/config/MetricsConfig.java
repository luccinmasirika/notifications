package com.irembo.notifications.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * Custom Prometheus Metrics Configuration.
 *
 * Exposes additional metrics beyond Spring Boot Actuator defaults:
 * - Redis connection status
 * - Cache statistics (hit/miss ratio)
 * - Custom application metrics
 *
 * All metrics are exposed on /actuator/prometheus endpoint.
 */
@Configuration
public class MetricsConfig {

    private static final Logger logger = LoggerFactory.getLogger(MetricsConfig.class);

    private final MeterRegistry meterRegistry;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;

    public MetricsConfig(
            MeterRegistry meterRegistry,
            RedisTemplate<String, Object> redisTemplate,
            CacheManager cacheManager) {
        this.meterRegistry = meterRegistry;
        this.redisTemplate = redisTemplate;
        this.cacheManager = cacheManager;
    }

    /**
     * Register custom metrics after bean initialization.
     */
    @PostConstruct
    public void registerCustomMetrics() {
        registerRedisMetrics();
        registerCacheMetrics();
        logger.info("Custom Prometheus metrics registered successfully");
    }

    /**
     * Register Redis connection status metric.
     *
     * Metric: redis_connection_status
     * - Value: 1.0 = connected, 0.0 = disconnected
     *
     * This helps monitor Redis availability without checking logs.
     * Alert if this metric drops to 0 for more than 1 minute.
     */
    private void registerRedisMetrics() {
        Gauge.builder("redis.connection.status", this, value -> {
                    try {
                        redisTemplate.getConnectionFactory().getConnection().ping();
                        return 1.0; // Connected
                    } catch (Exception e) {
                        logger.error("Redis connection check failed", e);
                        return 0.0; // Disconnected
                    }
                })
                .description("Redis connection status (1=connected, 0=disconnected)")
                .tag("component", "redis")
                .register(meterRegistry);

        logger.debug("Registered Redis connection status metric");
    }

    /**
     * Register cache-specific metrics.
     *
     * For Caffeine cache:
     * - cache_size: Current number of entries
     * - cache_hit_ratio: Percentage of cache hits
     * - cache_eviction_count: Number of evictions
     *
     * These metrics help monitor cache performance and tune cache size.
     */
    private void registerCacheMetrics() {
        // Register Caffeine cache metrics if using Caffeine
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache caffeineCache) {
                var nativeCache = caffeineCache.getNativeCache();

                // Bind Caffeine metrics to Micrometer
                CaffeineCacheMetrics.monitor(
                        meterRegistry,
                        nativeCache,
                        cacheName,
                        "cache", cacheName
                );

                logger.debug("Registered Caffeine cache metrics for: {}", cacheName);
            }
        });

        // Register custom cache health metric
        Gauge.builder("cache.health", this, value -> {
                    try {
                        // Check if at least one cache is available
                        return cacheManager.getCacheNames().isEmpty() ? 0.0 : 1.0;
                    } catch (Exception e) {
                        logger.error("Cache health check failed", e);
                        return 0.0;
                    }
                })
                .description("Cache health status (1=healthy, 0=unhealthy)")
                .tag("component", "cache")
                .register(meterRegistry);
    }
}
