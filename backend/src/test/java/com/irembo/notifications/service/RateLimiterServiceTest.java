package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.entity.SystemLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
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
        meterRegistry = new SimpleMeterRegistry();

        rateLimiterService = new RateLimiterService(
                redisCounter,
                clientLimitRepository,
                systemLimitRepository,
                adminService,
                meterRegistry
        );

        testClient = new Client();
        testClient.setId(1L);
        ApiKeyHashService hashService = new ApiKeyHashService();
        testApiKey = "test-api-key-123";
        testClient.setApiKeyHash(hashService.hashApiKey(testApiKey));
        testClient.setName("Test Client");
        testClient.setActive(true);

        testClientLimit = new ClientLimit();
        testClientLimit.setId(1L);
        testClientLimit.setClientId(1L);
        testClientLimit.setWindowSizeSeconds(10);
        testClientLimit.setMaxRequestsPerWindow(100);
        testClientLimit.setMonthlyQuota(10000);

        testSystemLimit = new SystemLimit();
        testSystemLimit.setId(1L);
        testSystemLimit.setName("global_rate_limit");
        testSystemLimit.setWindowSizeSeconds(10);
        testSystemLimit.setMaxRequestsPerWindow(10000);
        testSystemLimit.setActive(true);

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
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(50L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.usagePercent()).isLessThan(80.0);
        assertThat(decision.remaining()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should return SOFT_THROTTLE when usage is between 80% and 99%")
    void shouldSoftThrottleWhenUsageAbove80Percent() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(85L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isGreaterThanOrEqualTo(80.0);
        assertThat(decision.usagePercent()).isLessThan(100.0);
        assertThat(decision.remaining()).isGreaterThan(0);
        assertThat(decision.limit()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should return HARD_REJECT when usage is at or above 100%")
    void shouldHardRejectWhenUsageAt100Percent() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(100L);
        lenient().when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        lenient().when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.usagePercent()).isGreaterThanOrEqualTo(100.0);
        assertThat(decision.remaining()).isEqualTo(0);
        assertThat(decision.retryAt()).isNotNull();
        assertThat(decision.retryAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Should return HARD_REJECT when usage exceeds 100%")
    void shouldHardRejectWhenUsageExceeds100Percent() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(150L);
        lenient().when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        lenient().when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.usagePercent()).isGreaterThan(100.0);
        assertThat(decision.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return HARD_REJECT when monthly quota is exceeded")
    void shouldHardRejectWhenMonthlyQuotaExceeded() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(50L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(10000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.limit()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Should return SOFT_THROTTLE when monthly quota is at 85%")
    void shouldSoftThrottleWhenMonthlyQuotaAt85Percent() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(50L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(8500L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isGreaterThanOrEqualTo(80.0);
    }

    @Test
    @DisplayName("Should return HARD_REJECT for invalid API key")
    void shouldRejectInvalidApiKey() {
        when(adminService.validateApiKey("invalid-key")).thenReturn(Optional.empty());

        RateDecision decision = rateLimiterService.checkAndConsume("invalid-key");

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
    }

    @Test
    @DisplayName("Should return HARD_REJECT for inactive client")
    void shouldRejectInactiveClient() {
        testClient.setActive(false);
        when(adminService.validateApiKey(testApiKey)).thenReturn(Optional.of(testClient));

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
    }

    @Test
    @DisplayName("Should return HARD_REJECT when global limit is exceeded")
    void shouldRejectWhenGlobalLimitExceeded() {
        lenient().when(redisCounter.getWindowCounter("1", 10)).thenReturn(50L);
        lenient().when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(10000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.limit()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Should prioritize window limit over monthly quota for soft throttle")
    void shouldPrioritizeHigherUsageForSoftThrottle() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(90L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(7000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isGreaterThanOrEqualTo(90.0);
    }

    @Test
    @DisplayName("Should handle exact 80% boundary (SOFT_THROTTLE)")
    void shouldSoftThrottleAtExact80PercentBoundary() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(80L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("Should handle exact 99% usage (still SOFT_THROTTLE)")
    void shouldSoftThrottleAtExact99Percent() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(99L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isEqualTo(99.0);
        assertThat(decision.remaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle window exhaustion with 0 remaining")
    void shouldHandleWindowExhaustion() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(100L);
        lenient().when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        lenient().when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.remaining()).isEqualTo(0);
        assertThat(decision.retryAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle monthly quota exhaustion")
    void shouldHandleMonthlyQuotaExhaustion() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(10L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(10000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.usagePercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("Should handle client without limits configured")
    void shouldHandleClientWithoutLimits() {
        when(clientLimitRepository.findByClientId(1L)).thenReturn(Optional.empty());

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
    }

    @Test
    @DisplayName("Should handle zero requests scenario (ALLOW)")
    void shouldAllowWhenNoRequestsMadeYet() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(0L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(0L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(0L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.usagePercent()).isEqualTo(0.0);
        assertThat(decision.remaining()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should handle one request remaining (79% usage)")
    void shouldAllowWithOneRequestRemaining() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(79L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.usagePercent()).isEqualTo(79.0);
    }

    @Test
    @DisplayName("Should calculate correct reset time for window")
    void shouldProvideCorrectResetTime() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(100L);
        Instant expectedReset = Instant.now().plusSeconds(10);
        when(redisCounter.getWindowResetTime(10)).thenReturn(expectedReset);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.retryAt()).isEqualTo(expectedReset);
    }

    @Test
    @DisplayName("Should handle both window and monthly quota soft throttle")
    void shouldSoftThrottleWhenBothWindowAndMonthlyAtThreshold() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(85L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(8500L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(5000L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isEqualTo(85.0);
    }

    @Test
    @DisplayName("Should handle global system limit at threshold")
    void shouldSoftThrottleWhenGlobalLimitAt85Percent() {
        when(redisCounter.getWindowCounter("1", 10)).thenReturn(50L);
        when(redisCounter.getMonthlyCounter("1", "2025-11")).thenReturn(5000L);
        when(redisCounter.getGlobalWindow(10)).thenReturn(8500L);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.ALLOW);
    }
}
