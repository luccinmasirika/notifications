package com.irembo.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Service for validating request timestamps to prevent replay attacks.
 * 
 * Security:
 * - Prevents replay attacks by rejecting old requests
 * - Prevents clock skew attacks by rejecting future requests
 * - Configurable time window (default: ±5 minutes)
 * 
 * Performance:
 * - Validation: <0.1ms (simple timestamp comparison)
 */
@Service
public class TimestampValidator {

    private static final Logger logger = LoggerFactory.getLogger(TimestampValidator.class);
    
    // Default time window: 5 minutes (300,000 milliseconds)
    private static final long DEFAULT_TIME_WINDOW_MS = 5 * 60 * 1000L;

    private final long timeWindowMs;

    public TimestampValidator(@Value("${app.auth.timestamp-window-ms:300000}") long timeWindowMs) {
        this.timeWindowMs = timeWindowMs > 0 ? timeWindowMs : DEFAULT_TIME_WINDOW_MS;
        logger.info("TimestampValidator initialized with time window: {}ms ({} minutes)", 
                this.timeWindowMs, this.timeWindowMs / 60000);
    }

    /**
     * Validate a request timestamp.
     * 
     * @param requestTimestamp Unix timestamp in milliseconds from X-TIMESTAMP header
     * @return true if timestamp is within acceptable window, false otherwise
     */
    public boolean isValid(long requestTimestamp) {
        long currentTime = System.currentTimeMillis();
        long timeDifference = Math.abs(currentTime - requestTimestamp);
        
        boolean valid = timeDifference <= timeWindowMs;
        
        if (!valid) {
            logger.warn("Timestamp validation failed: request={}, current={}, difference={}ms, window={}ms",
                    requestTimestamp, currentTime, timeDifference, timeWindowMs);
        } else {
            logger.debug("Timestamp validation passed: difference={}ms", timeDifference);
        }
        
        return valid;
    }

    /**
     * Validate a request timestamp and return detailed result.
     * 
     * @param requestTimestamp Unix timestamp in milliseconds
     * @return ValidationResult with status and details
     */
    public ValidationResult validateWithDetails(long requestTimestamp) {
        long currentTime = System.currentTimeMillis();
        long timeDifference = currentTime - requestTimestamp;
        long absoluteDifference = Math.abs(timeDifference);
        
        if (absoluteDifference > timeWindowMs) {
            String reason = timeDifference > 0 
                    ? "Request timestamp is too old (replay attack possible)"
                    : "Request timestamp is in the future (clock skew)";
            
            return new ValidationResult(false, reason, timeDifference, timeWindowMs);
        }
        
        return new ValidationResult(true, "Timestamp is within acceptable window", timeDifference, timeWindowMs);
    }

    /**
     * Get the current timestamp in milliseconds (Unix epoch).
     * Useful for generating timestamps in tests or client examples.
     * 
     * @return Current Unix timestamp in milliseconds
     */
    public long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

    /**
     * Result of timestamp validation with details.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String reason;
        private final long timeDifferenceMs;
        private final long timeWindowMs;

        public ValidationResult(boolean valid, String reason, long timeDifferenceMs, long timeWindowMs) {
            this.valid = valid;
            this.reason = reason;
            this.timeDifferenceMs = timeDifferenceMs;
            this.timeWindowMs = timeWindowMs;
        }

        public boolean isValid() {
            return valid;
        }

        public String getReason() {
            return reason;
        }

        public long getTimeDifferenceMs() {
            return timeDifferenceMs;
        }

        public long getTimeWindowMs() {
            return timeWindowMs;
        }
    }
}

