package com.irembo.notifications.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TimestampValidatorTest {

    private TimestampValidator validator;
    private static final long WINDOW_MS = 120000; // 2 minutes

    @BeforeEach
    void setUp() {
        validator = new TimestampValidator(WINDOW_MS);
    }

    @Test
    @DisplayName("Should accept valid current timestamp")
    void shouldAcceptValidCurrentTimestamp() {
        long currentTimestamp = Instant.now().toEpochMilli();
        assertThat(validator.isValid(currentTimestamp)).isTrue();
    }

    @Test
    @DisplayName("Should accept timestamp within window")
    void shouldAcceptTimestampWithinWindow() {
        long timestamp = Instant.now().minusSeconds(60).toEpochMilli(); // 1 minute ago
        assertThat(validator.isValid(timestamp)).isTrue();
    }

    @Test
    @DisplayName("Should accept timestamp at window boundary")
    void shouldAcceptTimestampAtWindowBoundary() {
        long timestamp = Instant.now().minusMillis(WINDOW_MS).toEpochMilli();
        assertThat(validator.isValid(timestamp)).isTrue();
    }

    @Test
    @DisplayName("Should reject timestamp too old")
    void shouldRejectTimestampTooOld() {
        long timestamp = Instant.now().minusMillis(WINDOW_MS + 1000).toEpochMilli();
        assertThat(validator.isValid(timestamp)).isFalse();
    }

    @Test
    @DisplayName("Should reject timestamp too far in future")
    void shouldRejectTimestampTooFarInFuture() {
        long timestamp = Instant.now().plusMillis(WINDOW_MS + 1000).toEpochMilli();
        assertThat(validator.isValid(timestamp)).isFalse();
    }

    @Test
    @DisplayName("Should accept timestamp slightly in future")
    void shouldAcceptTimestampSlightlyInFuture() {
        long timestamp = Instant.now().plusSeconds(30).toEpochMilli();
        assertThat(validator.isValid(timestamp)).isTrue();
    }

    @Test
    @DisplayName("Should accept timestamp at future boundary")
    void shouldAcceptTimestampAtFutureBoundary() {
        long timestamp = Instant.now().plusMillis(WINDOW_MS).toEpochMilli();
        assertThat(validator.isValid(timestamp)).isTrue();
    }

    @Test
    @DisplayName("Should validate with details for valid timestamp")
    void shouldValidateWithDetailsForValidTimestamp() {
        long timestamp = Instant.now().toEpochMilli();
        TimestampValidator.ValidationResult result = validator.validateWithDetails(timestamp);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getReason()).contains("within acceptable window");
        assertThat(result.getTimeWindowMs()).isEqualTo(WINDOW_MS);
    }

    @Test
    @DisplayName("Should validate with details for old timestamp")
    void shouldValidateWithDetailsForOldTimestamp() {
        long timestamp = Instant.now().minusMillis(WINDOW_MS + 1000).toEpochMilli();
        TimestampValidator.ValidationResult result = validator.validateWithDetails(timestamp);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getReason()).contains("too old");
        assertThat(result.getTimeDifferenceMs()).isPositive();
    }

    @Test
    @DisplayName("Should validate with details for future timestamp")
    void shouldValidateWithDetailsForFutureTimestamp() {
        long timestamp = Instant.now().plusMillis(WINDOW_MS + 1000).toEpochMilli();
        TimestampValidator.ValidationResult result = validator.validateWithDetails(timestamp);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getReason()).contains("future");
        assertThat(result.getTimeDifferenceMs()).isNegative();
    }

    @Test
    @DisplayName("Should get current timestamp")
    void shouldGetCurrentTimestamp() {
        long timestamp1 = validator.getCurrentTimestamp();
        long timestamp2 = System.currentTimeMillis();

        assertThat(Math.abs(timestamp1 - timestamp2)).isLessThan(1000);
    }

    @Test
    @DisplayName("Should use default window when zero provided")
    void shouldUseDefaultWindowWhenZeroProvided() {
        TimestampValidator validatorWithZero = new TimestampValidator(0);
        long timestamp = Instant.now().minusSeconds(60).toEpochMilli();
        assertThat(validatorWithZero.isValid(timestamp)).isTrue();
    }
}

