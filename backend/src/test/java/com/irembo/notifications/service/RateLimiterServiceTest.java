package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.entity.SystemLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.infra.db.repository.SystemLimitRepository;
import com.irembo.notifications.infra.redis.RedisCounterRepository;
import com.irembo.notifications.model.dto.RateDecision;
import com.irembo.notifications.model.enums.DecisionType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private RedisCounterRepository redisCounter;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientLimitRepository clientLimitRepository;

    @Mock
    private SystemLimitRepository systemLimitRepository;

    @Mock
    private AdminService adminService;

    private SimpleMeterRegistry meterRegistry;

    private RateLimiterService rateLimiterService;

    private Client testClient;
    private ClientLimit testClientLimit;
    private SystemLimit testSystemLimit;
    private String testApiKey;

    @BeforeEach
    void setUp() {
        // Use a real SimpleMeterRegistry instead of mocking
        meterRegistry = new SimpleMeterRegistry();

        rateLimiterService = new RateLimiterService(
                redisCounter,
                clientRepository,
                clientLimitRepository,
                systemLimitRepository,
                adminService,
                meterRegistry
        );

        // Setup test client (V8: use hash instead of plain text API key)
        testClient = new Client();
        testClient.setId(1L);
        ApiKeyHashService hashService = new ApiKeyHashService();
        testApiKey = "test-api-key-123";
        testClient.setApiKeyHash(hashService.hashApiKey(testApiKey));
        testClient.setName("Test Client");
        testClient.setActive(true);

        // Setup test client limit
        testClientLimit = new ClientLimit();
        testClientLimit.setId(1L);
        testClientLimit.setClientId(1L);
        testClientLimit.setWindowSizeSeconds(10);
        testClientLimit.setMaxRequestsPerWindow(100);
        testClientLimit.setMonthlyQuota(10000);

        // Setup test system limit
        testSystemLimit = new SystemLimit();
        testSystemLimit.setId(1L);
        testSystemLimit.setName("global_rate_limit");
        testSystemLimit.setWindowSizeSeconds(10);
        testSystemLimit.setMaxRequestsPerWindow(10000);
        testSystemLimit.setActive(true);

        // Default mocks (lenient to avoid unnecessary stubbing warnings)
        // V8: RateLimiterService now uses AdminService.validateApiKey() to validate plain text API keys
        lenient().when(adminService.validateApiKey(testApiKey))
                .thenReturn(Optional.of(testClient));
        lenient().when(clientLimitRepository.findByClientId(1L))
                .thenReturn(Optional.of(testClientLimit));
        lenient().when(systemLimitRepository.findByNameAndActiveTrue("global_rate_limit"))
                .thenReturn(Optional.of(testSystemLimit));
        lenient().when(redisCounter.getCurrentYearMonth())
                .thenReturn("2025-11");
        lenient().when(redisCounter.getWindowResetTime(anyInt()))
                .thenReturn(Instant.now().plusSeconds(10));
    }

    @Test
    @DisplayName("Should return ALLOW when usage is below 80%")
    void shouldAllowWhenUsageBelowThreshold() {
        // Given: Usage is at 50% (50 out of 100 requests)
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(50L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.usagePercent()).isLessThan(80.0);
        assertThat(decision.remaining()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should return SOFT_THROTTLE when usage is between 80% and 99%")
    void shouldSoftThrottleWhenUsageAbove80Percent() {
        // Given: Usage is at 85% (85 out of 100 requests)
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(85L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isGreaterThanOrEqualTo(80.0);
        assertThat(decision.usagePercent()).isLessThan(100.0);
        assertThat(decision.remaining()).isGreaterThan(0);
        assertThat(decision.limit()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should return HARD_REJECT when usage is at or above 100%")
    void shouldHardRejectWhenUsageAt100Percent() {
        // Given: Usage is at 100% (100 out of 100 requests)
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(100L);
        lenient().when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        lenient().when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.usagePercent()).isGreaterThanOrEqualTo(100.0);
        assertThat(decision.remaining()).isEqualTo(0);
        assertThat(decision.retryAt()).isNotNull();
        assertThat(decision.retryAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Should return HARD_REJECT when usage exceeds 100%")
    void shouldHardRejectWhenUsageExceeds100Percent() {
        // Given: Usage exceeds 100% (150 out of 100 requests - due to race conditions)
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(150L);
        lenient().when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        lenient().when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.usagePercent()).isGreaterThan(100.0);
        assertThat(decision.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return HARD_REJECT when monthly quota is exceeded")
    void shouldHardRejectWhenMonthlyQuotaExceeded() {
        // Given: Window usage is fine, but monthly quota is exceeded
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(50L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(10000L); // At quota limit
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.limit()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Should return SOFT_THROTTLE when monthly quota is at 85%")
    void shouldSoftThrottleWhenMonthlyQuotaAt85Percent() {
        // Given: Monthly usage is at 85%
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(50L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(8500L); // 85% of 10000
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isGreaterThanOrEqualTo(80.0);
    }

    @Test
    @DisplayName("Should return HARD_REJECT for invalid API key")
    void shouldRejectInvalidApiKey() {
        // Given: Invalid API key
        // V8: Mock validateApiKey for invalid key (returns empty)
        when(adminService.validateApiKey("invalid-key")).thenReturn(Optional.empty());

        // When
        // V8: Pass plain text invalid key
        RateDecision decision = rateLimiterService.checkAndConsume("invalid-key", "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
    }

    @Test
    @DisplayName("Should return HARD_REJECT for inactive client")
    void shouldRejectInactiveClient() {
        // Given: Client is inactive
        testClient.setActive(false);
        // V8: Mock validateApiKey with the plain text API key
        when(adminService.validateApiKey(testApiKey)).thenReturn(Optional.of(testClient));

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
    }

    @Test
    @DisplayName("Should return HARD_REJECT when global limit is exceeded")
    void shouldRejectWhenGlobalLimitExceeded() {
        // Given: Client limits are fine, but global limit is exceeded
        lenient().when(redisCounter.getWindowCounter("1", 10)).thenReturn(50L);
        lenient().when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(10000L); // At global limit

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.limit()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Should prioritize window limit over monthly quota for soft throttle")
    void shouldPrioritizeHigherUsageForSoftThrottle() {
        // Given: Window at 90%, monthly at 70%
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(90L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(7000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isGreaterThanOrEqualTo(90.0); // Should use window's higher usage
    }

    @Test
    @DisplayName("Should handle exact 80% boundary (SOFT_THROTTLE)")
    void shouldSoftThrottleAtExact80PercentBoundary() {
        // Given: Exactly at 80% threshold (80 out of 100)
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(80L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("Should handle exact 99% usage (still SOFT_THROTTLE)")
    void shouldSoftThrottleAtExact99Percent() {
        // Given: At 99% (99 out of 100)
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(99L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isEqualTo(99.0);
        assertThat(decision.remaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle window exhaustion with 0 remaining")
    void shouldHandleWindowExhaustion() {
        // Given: Completely exhausted window
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(100L);
        lenient().when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        lenient().when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.remaining()).isEqualTo(0);
        assertThat(decision.retryAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle monthly quota exhaustion")
    void shouldHandleMonthlyQuotaExhaustion() {
        // Given: Monthly quota completely exhausted
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(10L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(10000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.usagePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Should handle client without limits configured")
    void shouldHandleClientWithoutLimits() {
        // Given: Client exists but has no limits
        when(clientLimitRepository.findByClientId(1L)).thenReturn(Optional.empty());

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
    }

    @Test
    @DisplayName("Should handle zero requests scenario (ALLOW)")
    void shouldAllowWhenNoRequestsMadeYet() {
        // Given: No requests made yet (0 usage)
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(0L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(0L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(0L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.usagePercent()).isEqualTo(0.0);
        assertThat(decision.remaining()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should handle one request remaining (79% usage)")
    void shouldAllowWithOneRequestRemaining() {
        // Given: 79 out of 100 requests (still under 80%)
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(79L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.usagePercent()).isEqualTo(79.0);
    }

    @Test
    @DisplayName("Should calculate correct reset time for window")
    void shouldProvideCorrectResetTime() {
        // Given: At limit
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(100L);
        Instant expectedReset = Instant.now().plusSeconds(10);
        when(redisCounter.getWindowResetTime(10)).thenReturn(expectedReset);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.retryAt()).isEqualTo(expectedReset);
    }

    @Test
    @DisplayName("Should handle both window and monthly quota soft throttle")
    void shouldSoftThrottleWhenBothWindowAndMonthlyAtThreshold() {
        // Given: Both at 85%
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(85L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(8500L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then
        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isEqualTo(85.0);
    }

    @Test
    @DisplayName("Should handle global system limit at threshold")
    void shouldSoftThrottleWhenGlobalLimitAt85Percent() {
        // Given: Client fine, global at 85%
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(50L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(8500L); // 85% of 10000

        // When
        // V8: RateLimiterService now expects plain text API key (validated via AdminService)
        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey, "SMS");

        // Then: global limit soft-throttle is logged, but final decision remains ALLOW
        // because client-specific usage is still below soft-throttle threshold.
        assertThat(decision.type()).isEqualTo(DecisionType.ALLOW);
    }
}
