package com.irembo.notifications.model.dto;

import com.irembo.notifications.infra.db.entity.ClientLimit;

import java.time.Instant;

public record ClientDetailsResponse(
    ClientDto client,
    ClientLimit limit,
    WindowUsage windowUsage,
    MonthlyUsage monthlyUsage,
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

