package com.irembo.notifications.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TimestampValidator {

    private static final Logger logger = LoggerFactory.getLogger(TimestampValidator.class);
    
    private static final long DEFAULT_TIME_WINDOW_MS = 5 * 60 * 1000L;

    private final long timeWindowMs;

    public TimestampValidator(@Value("${app.auth.timestamp-window-ms:300000}") long timeWindowMs) {
        this.timeWindowMs = timeWindowMs > 0 ? timeWindowMs : DEFAULT_TIME_WINDOW_MS;
        logger.info("TimestampValidator initialized with time window: {}ms ({} minutes)", 
                this.timeWindowMs, this.timeWindowMs / 60000);
    }

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

    public long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }

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

