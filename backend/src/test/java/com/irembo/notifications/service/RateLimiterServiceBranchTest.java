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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceBranchTest {

    @Mock
    private RedisCounterRepository redisCounter;
    @Mock
    private ClientLimitRepository clientLimitRepository;
    @Mock
    private SystemLimitRepository systemLimitRepository;
    @Mock
    private AdminService adminService;

    private RateLimiterService service;
    private Client activeClient;
    private ClientLimit clientLimit;
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @BeforeEach
    void setUp() {
        service = new RateLimiterService(
                redisCounter,
                clientLimitRepository,
                systemLimitRepository,
                adminService,
                meterRegistry
        );

        ReflectionTestUtils.setField(service, "defaultSoftThrottleThreshold", 0.8);
        ReflectionTestUtils.setField(service, "defaultHardRejectThreshold", 1.0);
        ReflectionTestUtils.setField(service, "invalidApiKeyRetryDelaySeconds", 60);

        activeClient = new Client();
        activeClient.setId(1L);
        activeClient.setActive(true);
        activeClient.setStatus("ACTIVE");

        clientLimit = new ClientLimit();
        clientLimit.setClientId(1L);
        clientLimit.setWindowSizeSeconds(60);
        clientLimit.setMaxRequestsPerWindow(100);
        clientLimit.setMonthlyQuota(1000);
        clientLimit.setSoftThrottleThreshold(0.8);
        clientLimit.setHardRejectThreshold(1.0);

    }

    @Test
    @DisplayName("Invalid API key should hard reject and set retry time")
    void invalidApiKeyHardRejects() {
        when(adminService.validateApiKey("bad")).thenReturn(Optional.empty());

        RateDecision decision = service.checkAndConsume("bad");

        assertEquals(DecisionType.HARD_REJECT, decision.type());
        assertNotNull(decision.retryAt());
        assertTrue(meterRegistry.get("ratelimiter.hard_reject").counter().count() > 0);
        assertTrue(meterRegistry.get("ratelimiter.invalid_api_key").counter().count() > 0);
    }

    @Test
    @DisplayName("Missing client limit should hard reject")
    void missingClientLimitHardRejects() {
        when(adminService.validateApiKey("key")).thenReturn(Optional.of(activeClient));
        when(clientLimitRepository.findByClientId(1L)).thenReturn(Optional.empty());

        RateDecision decision = service.checkAndConsume("key");

        assertEquals(DecisionType.HARD_REJECT, decision.type());
    }

    @Test
    @DisplayName("Soft throttle when window decision is soft")
    void softThrottleWhenThresholdReached() {
        when(adminService.validateApiKey("key")).thenReturn(Optional.of(activeClient));
        when(clientLimitRepository.findByClientId(1L)).thenReturn(Optional.of(clientLimit));
        when(systemLimitRepository.findByNameAndActiveTrue(any())).thenReturn(Optional.empty());
        when(redisCounter.getCurrentYearMonth()).thenReturn("2024-01");

        var windowResult = new RedisCounterRepository.AtomicCheckResult(true, 90, 90.0, 1);
        var monthlyResult = new RedisCounterRepository.AtomicCheckResult(true, 100, 10.0, 0);
        var batch = new RedisCounterRepository.AtomicBatchCheckResult(windowResult, monthlyResult, null, true);

        when(redisCounter.atomicCheckAndIncrementBatch(any(), any(), any(long.class), any(double.class), any(double.class),
                any(int.class), any(), any(long.class), any(double.class), any(double.class), any(int.class),
                any(), any(), any(int.class)))
                .thenReturn(batch);
        when(redisCounter.getWindowResetTime(any(int.class))).thenAnswer(inv ->
                Instant.now().plusSeconds(((Number) inv.getArgument(0)).longValue()));
        when(redisCounter.getMonthlyResetTime()).thenReturn(Instant.now().plusSeconds(3600));

        RateDecision decision = service.checkAndConsume("key");

        assertEquals(DecisionType.SOFT_THROTTLE, decision.type());
        assertEquals(100, decision.limit());
        assertTrue(meterRegistry.get("ratelimiter.soft_throttle").counter().count() > 0);
    }

    @Test
    @DisplayName("Global hard reject when global decision is restrictive")
    void globalHardReject() {
        when(adminService.validateApiKey("key")).thenReturn(Optional.of(activeClient));
        when(clientLimitRepository.findByClientId(1L)).thenReturn(Optional.of(clientLimit));
        when(redisCounter.getCurrentYearMonth()).thenReturn("2024-01");

        SystemLimit global = new SystemLimit();
        global.setName("global_rate_limit");
        global.setWindowSizeSeconds(30);
        global.setMaxRequestsPerWindow(50);
        global.setActive(true);
        when(systemLimitRepository.findByNameAndActiveTrue("global_rate_limit")).thenReturn(Optional.of(global));

        var windowResult = new RedisCounterRepository.AtomicCheckResult(true, 10, 10.0, 0);
        var monthlyResult = new RedisCounterRepository.AtomicCheckResult(true, 20, 2.0, 0);
        var globalResult = new RedisCounterRepository.AtomicCheckResult(false, 60, 120.0, 2);
        var batch = new RedisCounterRepository.AtomicBatchCheckResult(windowResult, monthlyResult, globalResult, false);

        when(redisCounter.atomicCheckAndIncrementBatch(any(), any(), any(long.class), any(double.class), any(double.class),
                any(int.class), any(), any(long.class), any(double.class), any(double.class), any(int.class),
                any(), any(), any(int.class)))
                .thenReturn(batch);
        when(redisCounter.getWindowResetTime(any(int.class))).thenAnswer(inv ->
                Instant.now().plusSeconds(((Number) inv.getArgument(0)).longValue()));
        when(redisCounter.getMonthlyResetTime()).thenReturn(Instant.now().plusSeconds(3600));

        RateDecision decision = service.checkAndConsume("key");

        assertEquals(DecisionType.HARD_REJECT, decision.type());
        assertEquals(50, decision.limit());
    }

    @Test
    @DisplayName("Allow when all counters are within thresholds")
    void allowWhenWithinLimits() {
        when(adminService.validateApiKey("key")).thenReturn(Optional.of(activeClient));
        when(clientLimitRepository.findByClientId(1L)).thenReturn(Optional.of(clientLimit));
        when(systemLimitRepository.findByNameAndActiveTrue(any())).thenReturn(Optional.empty());
        when(redisCounter.getCurrentYearMonth()).thenReturn("2024-01");

        var windowResult = new RedisCounterRepository.AtomicCheckResult(true, 10, 10.0, 0);
        var monthlyResult = new RedisCounterRepository.AtomicCheckResult(true, 5, 0.5, 0);
        var batch = new RedisCounterRepository.AtomicBatchCheckResult(windowResult, monthlyResult, null, true);

        when(redisCounter.atomicCheckAndIncrementBatch(any(), any(), any(long.class), any(double.class), any(double.class),
                any(int.class), any(), any(long.class), any(double.class), any(double.class), any(int.class),
                any(), any(), any(int.class)))
                .thenReturn(batch);
        Instant reset = Instant.now().plusSeconds(60);
        when(redisCounter.getWindowResetTime(any(int.class))).thenAnswer(inv -> reset);
        when(redisCounter.getMonthlyResetTime()).thenReturn(Instant.now().plusSeconds(3600));

        RateDecision decision = service.checkAndConsume("key");

        assertEquals(DecisionType.ALLOW, decision.type());
        assertEquals(90, decision.remaining());
        assertEquals(reset, decision.reset());
    }
}
