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

    @PostConstruct
    public void registerCustomMetrics() {
        registerRedisMetrics();
        registerCacheMetrics();
        logger.info("Custom Prometheus metrics registered successfully");
    }

    private void registerRedisMetrics() {
        Gauge.builder("redis.connection.status", this, value -> {
                    try {
                        redisTemplate.getConnectionFactory().getConnection().ping();
                        return 1.0;
                    } catch (Exception e) {
                        logger.error("Redis connection check failed", e);
                        return 0.0;
                    }
                })
                .description("Redis connection status (1=connected, 0=disconnected)")
                .tag("component", "redis")
                .register(meterRegistry);

        logger.debug("Registered Redis connection status metric");
    }

    private void registerCacheMetrics() {
        cacheManager.getCacheNames().forEach(cacheName -> {
            var cache = cacheManager.getCache(cacheName);
            if (cache instanceof CaffeineCache caffeineCache) {
                var nativeCache = caffeineCache.getNativeCache();

                CaffeineCacheMetrics.monitor(
                        meterRegistry,
                        nativeCache,
                        cacheName,
                        "cache", cacheName
                );

                logger.debug("Registered Caffeine cache metrics for: {}", cacheName);
            }
        });

        Gauge.builder("cache.health", this, value -> {
                    try {
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
