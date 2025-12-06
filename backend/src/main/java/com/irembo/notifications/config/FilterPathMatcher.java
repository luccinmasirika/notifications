package com.irembo.notifications.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class FilterPathMatcher {

    private List<String> skipPatterns;

    public FilterPathMatcher() {
    }

    @Value("${app.filter.skip-paths:/health,/actuator/health,/actuator/**,/admin/**,/swagger-ui**,/v3/api-docs**,/api-docs**,/error**}")
    public void setSkipPaths(String skipPaths) {
        this.skipPatterns = Arrays.asList(skipPaths.split(","));
    }

    public static FilterPathMatcher forTesting(String skipPaths) {
        FilterPathMatcher matcher = new FilterPathMatcher();
        matcher.setSkipPaths(skipPaths);
        return matcher;
    }

    public boolean shouldSkip(String path) {
        if (path == null) {
            return false;
        }

        for (String pattern : skipPatterns) {
            String trimmedPattern = pattern.trim();
            
            if (path.equals(trimmedPattern)) {
                return true;
            }
            
            if (trimmedPattern.endsWith("**")) {
                String prefix = trimmedPattern.substring(0, trimmedPattern.length() - 2);
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
            
            if (path.startsWith(trimmedPattern)) {
                return true;
            }
        }
        
        return false;
    }
}
