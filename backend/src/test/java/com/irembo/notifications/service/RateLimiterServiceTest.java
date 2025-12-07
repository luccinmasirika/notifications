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
import static org.mockito.ArgumentMatchers.*;
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
        lenient().when(redisCounter.getMonthlyResetTime())
                .thenReturn(Instant.now().plusSeconds(30 * 24 * 60 * 60));
    }

    private RedisCounterRepository.AtomicCheckResult createAtomicCheckResult(int decision, long count, double usagePercent) {
        boolean allowed = decision < 2;
        return new RedisCounterRepository.AtomicCheckResult(allowed, count, usagePercent, decision);
    }

    private RedisCounterRepository.AtomicBatchCheckResult createBatchResult(
            int windowDecision, long windowCount, double windowUsage,
            int monthlyDecision, long monthlyCount, double monthlyUsage,
            Integer globalDecision, Long globalCount, Double globalUsage) {
        RedisCounterRepository.AtomicCheckResult windowResult = createAtomicCheckResult(windowDecision, windowCount, windowUsage);
        RedisCounterRepository.AtomicCheckResult monthlyResult = createAtomicCheckResult(monthlyDecision, monthlyCount, monthlyUsage);
        RedisCounterRepository.AtomicCheckResult globalResult = (globalDecision != null) 
            ? createAtomicCheckResult(globalDecision, globalCount, globalUsage) : null;
        boolean allAllowed = (windowDecision < 2) && (monthlyDecision < 2) && (globalDecision == null || globalDecision < 2);
        return new RedisCounterRepository.AtomicBatchCheckResult(windowResult, monthlyResult, globalResult, allAllowed);
    }

    @Test
    @DisplayName("Should return ALLOW when usage is below 80%")
    void shouldAllowWhenUsageBelowThreshold() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            0, 50L, 50.0,  // window: ALLOW, 50/100 = 50%
            0, 5000L, 50.0,  // monthly: ALLOW, 5000/10000 = 50%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.usagePercent()).isLessThan(80.0);
        assertThat(decision.remaining()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should return SOFT_THROTTLE when usage is between 80% and 99%")
    void shouldSoftThrottleWhenUsageAbove80Percent() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            1, 85L, 85.0,  // window: SOFT_THROTTLE, 85/100 = 85%
            0, 5000L, 50.0,  // monthly: ALLOW, 5000/10000 = 50%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

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
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            2, 100L, 100.0,  // window: HARD_REJECT, 100/100 = 100%
            0, 5000L, 50.0,  // monthly: ALLOW, 5000/10000 = 50%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

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
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            2, 150L, 150.0,  // window: HARD_REJECT, 150/100 = 150%
            0, 5000L, 50.0,  // monthly: ALLOW, 5000/10000 = 50%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.usagePercent()).isGreaterThan(100.0);
        assertThat(decision.remaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should return HARD_REJECT when monthly quota is exceeded")
    void shouldHardRejectWhenMonthlyQuotaExceeded() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            0, 50L, 50.0,  // window: ALLOW, 50/100 = 50%
            2, 10000L, 100.0,  // monthly: HARD_REJECT, 10000/10000 = 100%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.limit()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Should return SOFT_THROTTLE when monthly quota is at 85%")
    void shouldSoftThrottleWhenMonthlyQuotaAt85Percent() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            0, 50L, 50.0,  // window: ALLOW, 50/100 = 50%
            1, 8500L, 85.0,  // monthly: SOFT_THROTTLE, 8500/10000 = 85%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

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
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            0, 50L, 50.0,  // window: ALLOW, 50/100 = 50%
            0, 5000L, 50.0,  // monthly: ALLOW, 5000/10000 = 50%
            2, 10000L, 100.0  // global: HARD_REJECT, 10000/10000 = 100%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.limit()).isEqualTo(10000L);
    }

    @Test
    @DisplayName("Should prioritize window limit over monthly quota for soft throttle")
    void shouldPrioritizeHigherUsageForSoftThrottle() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            1, 90L, 90.0,  // window: SOFT_THROTTLE, 90/100 = 90%
            1, 7000L, 70.0,  // monthly: SOFT_THROTTLE, 7000/10000 = 70%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isGreaterThanOrEqualTo(90.0);
    }

    @Test
    @DisplayName("Should handle exact 80% boundary (SOFT_THROTTLE)")
    void shouldSoftThrottleAtExact80PercentBoundary() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            1, 80L, 80.0,  // window: SOFT_THROTTLE, 80/100 = 80%
            0, 5000L, 50.0,  // monthly: ALLOW, 5000/10000 = 50%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isEqualTo(80.0);
    }

    @Test
    @DisplayName("Should handle exact 99% usage (still SOFT_THROTTLE)")
    void shouldSoftThrottleAtExact99Percent() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            1, 99L, 99.0,  // window: SOFT_THROTTLE, 99/100 = 99%
            0, 5000L, 50.0,  // monthly: ALLOW, 5000/10000 = 50%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isEqualTo(99.0);
        assertThat(decision.remaining()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should handle window exhaustion with 0 remaining")
    void shouldHandleWindowExhaustion() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            2, 100L, 100.0,  // window: HARD_REJECT, 100/100 = 100%
            0, 5000L, 50.0,  // monthly: ALLOW, 5000/10000 = 50%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.remaining()).isEqualTo(0);
        assertThat(decision.retryAt()).isNotNull();
    }

    @Test
    @DisplayName("Should handle monthly quota exhaustion")
    void shouldHandleMonthlyQuotaExhaustion() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            0, 10L, 10.0,  // window: ALLOW, 10/100 = 10%
            2, 10000L, 100.0,  // monthly: HARD_REJECT, 10000/10000 = 100%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

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
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            0, 0L, 0.0,  // window: ALLOW, 0/100 = 0%
            0, 0L, 0.0,  // monthly: ALLOW, 0/10000 = 0%
            0, 0L, 0.0  // global: ALLOW, 0/10000 = 0%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.usagePercent()).isEqualTo(0.0);
        assertThat(decision.remaining()).isEqualTo(100);
    }

    @Test
    @DisplayName("Should handle one request remaining (79% usage)")
    void shouldAllowWithOneRequestRemaining() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            0, 79L, 79.0,  // window: ALLOW, 79/100 = 79%
            0, 5000L, 50.0,  // monthly: ALLOW, 5000/10000 = 50%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.ALLOW);
        assertThat(decision.usagePercent()).isEqualTo(79.0);
    }

    @Test
    @DisplayName("Should calculate correct reset time for window")
    void shouldProvideCorrectResetTime() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            2, 100L, 100.0,  // window: HARD_REJECT, 100/100 = 100%
            0, 5000L, 50.0,  // monthly: ALLOW, 5000/10000 = 50%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);
        Instant expectedReset = Instant.now().plusSeconds(10);
        when(redisCounter.getWindowResetTime(10)).thenReturn(expectedReset);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.HARD_REJECT);
        assertThat(decision.retryAt()).isEqualTo(expectedReset);
    }

    @Test
    @DisplayName("Should handle both window and monthly quota soft throttle")
    void shouldSoftThrottleWhenBothWindowAndMonthlyAtThreshold() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            1, 85L, 85.0,  // window: SOFT_THROTTLE, 85/100 = 85%
            1, 8500L, 85.0,  // monthly: SOFT_THROTTLE, 8500/10000 = 85%
            0, 5000L, 50.0  // global: ALLOW, 5000/10000 = 50%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
        assertThat(decision.usagePercent()).isEqualTo(85.0);
    }

    @Test
    @DisplayName("Should handle global system limit at threshold")
    void shouldSoftThrottleWhenGlobalLimitAt85Percent() {
        RedisCounterRepository.AtomicBatchCheckResult batchResult = createBatchResult(
            0, 50L, 50.0,  // window: ALLOW, 50/100 = 50%
            0, 5000L, 50.0,  // monthly: ALLOW, 5000/10000 = 50%
            1, 8500L, 85.0  // global: SOFT_THROTTLE, 8500/10000 = 85%
        );
        when(redisCounter.atomicCheckAndIncrementBatch(anyString(), anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(),
                anyString(), anyLong(), anyDouble(), anyDouble(), anyInt(), anyString(), any(), anyInt()))
                .thenReturn(batchResult);

        RateDecision decision = rateLimiterService.checkAndConsume(testApiKey);

        assertThat(decision.type()).isEqualTo(DecisionType.SOFT_THROTTLE);
    }
}
