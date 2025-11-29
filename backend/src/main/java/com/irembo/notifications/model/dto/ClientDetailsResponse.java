package com.irembo.notifications.model.dto;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;

import java.time.Instant;

/**
 * Complete client details including usage statistics, limits, and status.
 */
public record ClientDetailsResponse(
    // Client information
    Client client,
    
    // Rate limit configuration
    ClientLimit limit,
    
    // Window usage statistics
    WindowUsage windowUsage,
    
    // Monthly usage statistics
    MonthlyUsage monthlyUsage,
    
    // Current status
    ClientStatus status
) {
    public record WindowUsage(
        long currentCount,
        long maxRequests,
        int windowSizeSeconds,
        double usagePercent,
        long remaining,
        Instant resetTime,
        boolean isSoftThrottled,
        boolean isBlocked
    ) {}
    
    public record MonthlyUsage(
        long currentCount,
        long monthlyQuota,
        double usagePercent,
        long remaining,
        boolean isSoftThrottled,
        boolean isBlocked
    ) {}
    
    public record ClientStatus(
        boolean isActive,
        boolean isBlocked,
        boolean isSoftThrottled,
        String statusMessage,
        Instant nextReset
    ) {}
}

