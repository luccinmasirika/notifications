package com.irembo.notifications.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class for matching paths against skip patterns.
 * Used by filters to determine if authentication/rate limiting should be skipped.
 */
@Component
public class FilterPathMatcher {

    private List<String> skipPatterns;

    /**
     * Default constructor for Spring.
     */
    public FilterPathMatcher() {
        // Spring will call setSkipPaths() via @Value
    }

    /**
     * Setter for Spring dependency injection with @Value.
     */
    @Value("${app.filter.skip-paths:/health,/actuator/health,/actuator/**,/admin/**,/swagger-ui**,/v3/api-docs**,/api-docs**,/error**}")
    public void setSkipPaths(String skipPaths) {
        this.skipPatterns = Arrays.asList(skipPaths.split(","));
    }

    /**
     * Factory method for testing without Spring context.
     * Allows creating instances directly with a skip paths string.
     */
    public static FilterPathMatcher forTesting(String skipPaths) {
        FilterPathMatcher matcher = new FilterPathMatcher();
        matcher.setSkipPaths(skipPaths);
        return matcher;
    }

    /**
     * Check if a path should be skipped (no authentication/rate limiting).
     *
     * @param path Request path
     * @return true if path matches any skip pattern
     */
    public boolean shouldSkip(String path) {
        if (path == null) {
            return false;
        }

        for (String pattern : skipPatterns) {
            String trimmedPattern = pattern.trim();
            
            // Exact match
            if (path.equals(trimmedPattern)) {
                return true;
            }
            
            // Ends with ** means startsWith
            if (trimmedPattern.endsWith("**")) {
                String prefix = trimmedPattern.substring(0, trimmedPattern.length() - 2);
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
            
            // Starts with pattern
            if (path.startsWith(trimmedPattern)) {
                return true;
            }
        }
        
        return false;
    }
}
