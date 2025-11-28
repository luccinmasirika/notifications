package com.irembo.notifications.model.dto;

import com.irembo.notifications.model.enums.DecisionType;

import java.time.Instant;

public record RateDecision(
    DecisionType type,
    double usagePercent,
    Instant retryAt,
    long limit,
    long remaining,
    Instant reset
) {
    public static RateDecision allow(long limit, long remaining, Instant reset, double usagePercent) {
        return new RateDecision(DecisionType.ALLOW, usagePercent, null, limit, remaining, reset);
    }

    public static RateDecision softThrottle(long limit, long remaining, Instant reset, double usagePercent) {
        return new RateDecision(DecisionType.SOFT_THROTTLE, usagePercent, null, limit, remaining, reset);
    }

    public static RateDecision hardReject(long limit, Instant retryAt, double usagePercent) {
        return new RateDecision(DecisionType.HARD_REJECT, usagePercent, retryAt, limit, 0, retryAt);
    }
}
