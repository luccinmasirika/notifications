package com.irembo.notifications.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Random;

@Configuration
public class NotificationProcessingConfig {

    @Value("${app.notification.delay.min-ms:1000}")
    private int minDelayMs;

    @Value("${app.notification.delay.max-ms:3000}")
    private int maxDelayMs;

    private final Random random = new Random();

    /**
     * Generate a processing delay between minDelayMs and maxDelayMs (inclusive).
     * Used to simulate asynchronous notification processing.
     *
     * @return delay in milliseconds
     */
    public int generateProcessingDelay() {
        if (minDelayMs >= maxDelayMs) {
            return minDelayMs;
        }
        return minDelayMs + random.nextInt(maxDelayMs - minDelayMs + 1);
    }

    public int getMinDelayMs() {
        return minDelayMs;
    }

    public int getMaxDelayMs() {
        return maxDelayMs;
    }
}
