package com.irembo.notifications.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Actuator configuration with custom health indicators.
 */
@Configuration
public class ActuatorConfig {

    /**
     * Custom health indicator for PostgreSQL database.
     */
    @Bean
    public HealthIndicator databaseHealthIndicator(DataSource dataSource) {
        return () -> {
            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(2)) {
                    return Health.up()
                            .withDetail("database", "PostgreSQL")
                            .withDetail("status", "UP")
                            .build();
                }
            } catch (Exception e) {
                return Health.down()
                        .withDetail("database", "PostgreSQL")
                        .withDetail("error", e.getMessage())
                        .build();
            }
            return Health.down()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("status", "Connection invalid")
                    .build();
        };
    }

    /**
     * Custom health indicator for Redis cache.
     */
    @Bean
    public HealthIndicator redisHealthIndicator(RedisConnectionFactory redisConnectionFactory) {
        return () -> {
            try {
                RedisTemplate<String, String> template = new RedisTemplate<>();
                template.setConnectionFactory(redisConnectionFactory);
                template.afterPropertiesSet();

                // Test ping
                String pong = template.getConnectionFactory().getConnection().ping();
                if ("PONG".equals(pong)) {
                    return Health.up()
                            .withDetail("cache", "Redis")
                            .withDetail("status", "UP")
                            .withDetail("ping", pong)
                            .build();
                }
            } catch (Exception e) {
                return Health.down()
                        .withDetail("cache", "Redis")
                        .withDetail("error", e.getMessage())
                        .build();
            }
            return Health.down()
                    .withDetail("cache", "Redis")
                    .withDetail("status", "Ping failed")
                    .build();
        };
    }
}
