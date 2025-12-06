package com.irembo.notifications.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.beans.factory.annotation.Value;
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

@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger logger = LoggerFactory.getLogger(CacheConfig.class);

    @Value("${cache.type:redis}")
    private String cacheType;

    @Value("${app.cache.api-key-validation-ttl-hours:24}")
    private int apiKeyValidationTtlHours;

    @Value("${app.cache.default-ttl-minutes:5}")
    private int defaultTtlMinutes;

    @Value("${app.cache.caffeine-max-size:500}")
    private int caffeineMaxSize;

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

    private ObjectMapper createRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    private GenericJackson2JsonRedisSerializer createRedisSerializer() {
        return new GenericJackson2JsonRedisSerializer(createRedisObjectMapper());
    }

    private CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer serializer = createRedisSerializer();
        
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(defaultTtlMinutes))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer)
                )
                .disableCachingNullValues();

        RedisCacheConfiguration apiKeyValidationConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(apiKeyValidationTtlHours))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(serializer)
                )
                .disableCachingNullValues();

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("apiKeyValidation", apiKeyValidationConfig)
                .transactionAware()
                .build();
    }

    private CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager("clientConfigs", "systemLimits", "apiKeyValidation");
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(caffeineMaxSize)
                .expireAfterWrite(defaultTtlMinutes, TimeUnit.MINUTES)
                .recordStats());
        return cacheManager;
    }
}
