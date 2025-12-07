package com.irembo.notifications.infra.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisCounterRepositoryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RedisCounterRepository repository;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Should increment window counter")
    void shouldIncrementWindowCounter() {
        String clientId = "client123";
        int windowSeconds = 10;
        Long expectedCount = 5L;

        when(valueOperations.increment(anyString())).thenReturn(expectedCount);

        long count = repository.incrementWindowCounter(clientId, windowSeconds);

        assertEquals(expectedCount, count);
        verify(valueOperations).increment(anyString());
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("Should set expiration on first increment")
    void shouldSetExpirationOnFirstIncrement() {
        String clientId = "client123";
        int windowSeconds = 10;

        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), eq((long) windowSeconds), eq(TimeUnit.SECONDS))).thenReturn(true);

        repository.incrementWindowCounter(clientId, windowSeconds);

        verify(redisTemplate).expire(anyString(), eq((long) windowSeconds), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("Should not set expiration on subsequent increments")
    void shouldNotSetExpirationOnSubsequentIncrements() {
        String clientId = "client123";
        int windowSeconds = 10;

        when(valueOperations.increment(anyString())).thenReturn(5L);

        repository.incrementWindowCounter(clientId, windowSeconds);

        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("Should get window counter")
    void shouldGetWindowCounter() {
        String clientId = "client123";
        int windowSeconds = 10;
        String expectedValue = "10";

        when(valueOperations.get(anyString())).thenReturn(expectedValue);

        long count = repository.getWindowCounter(clientId, windowSeconds);

        assertEquals(10L, count);
        verify(valueOperations).get(anyString());
    }

    @Test
    @DisplayName("Should return zero when window counter not found")
    void shouldReturnZeroWhenWindowCounterNotFound() {
        String clientId = "client123";
        int windowSeconds = 10;

        when(valueOperations.get(anyString())).thenReturn(null);

        long count = repository.getWindowCounter(clientId, windowSeconds);

        assertEquals(0L, count);
    }

    @Test
    @DisplayName("Should increment monthly counter")
    void shouldIncrementMonthlyCounter() {
        String clientId = "client123";
        String yearMonth = "2024-01";
        Long expectedCount = 100L;

        when(valueOperations.increment(anyString())).thenReturn(expectedCount);

        long count = repository.incrementMonthlyCounter(clientId, yearMonth);

        assertEquals(expectedCount, count);
        verify(valueOperations).increment(anyString());
        verify(redisTemplate, never()).expire(anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("Should get monthly counter")
    void shouldGetMonthlyCounter() {
        String clientId = "client123";
        String yearMonth = "2024-01";
        String expectedValue = "500";

        when(valueOperations.get(anyString())).thenReturn(expectedValue);

        long count = repository.getMonthlyCounter(clientId, yearMonth);

        assertEquals(500L, count);
    }

    @Test
    @DisplayName("Should increment global window counter")
    void shouldIncrementGlobalWindowCounter() {
        int windowSeconds = 60;
        Long expectedCount = 1000L;

        when(valueOperations.increment(anyString())).thenReturn(expectedCount);

        long count = repository.incrementGlobalWindow(windowSeconds);

        assertEquals(expectedCount, count);
        verify(valueOperations).increment(anyString());
    }

    @Test
    @DisplayName("Should get global window counter")
    void shouldGetGlobalWindowCounter() {
        int windowSeconds = 60;
        String expectedValue = "2000";

        when(valueOperations.get(anyString())).thenReturn(expectedValue);

        long count = repository.getGlobalWindow(windowSeconds);

        assertEquals(2000L, count);
    }

    @Test
    @DisplayName("Should get window reset time")
    void shouldGetWindowResetTime() {
        int windowSeconds = 10;
        Instant now = Instant.now();
        
        Instant resetTime = repository.getWindowResetTime(windowSeconds);
        
        assertNotNull(resetTime);
        assertTrue(resetTime.isAfter(now));
    }

    @Test
    @DisplayName("Should get current year month")
    void shouldGetCurrentYearMonth() {
        String yearMonth = repository.getCurrentYearMonth();
        
        assertNotNull(yearMonth);
        assertTrue(yearMonth.matches("\\d{4}-\\d{2}"));
        assertEquals(YearMonth.now().toString(), yearMonth);
    }

    @Test
    @DisplayName("Should get monthly reset time")
    void shouldGetMonthlyResetTime() {
        Instant resetTime = repository.getMonthlyResetTime();
        
        assertNotNull(resetTime);
        assertTrue(resetTime.isAfter(Instant.now()));
    }

    @Test
    @DisplayName("Should handle null increment result")
    void shouldHandleNullIncrementResult() {
        String clientId = "client123";
        int windowSeconds = 10;

        when(valueOperations.increment(anyString())).thenReturn(null);

        long count = repository.incrementWindowCounter(clientId, windowSeconds);

        assertEquals(0L, count);
    }

    @Test
    @DisplayName("Should handle null get result")
    void shouldHandleNullGetResult() {
        String clientId = "client123";
        int windowSeconds = 10;

        when(valueOperations.get(anyString())).thenReturn(null);

        long count = repository.getWindowCounter(clientId, windowSeconds);

        assertEquals(0L, count);
    }

    @Test
    @DisplayName("Should format window key correctly")
    void shouldFormatWindowKeyCorrectly() {
        String clientId = "client123";
        int windowSeconds = 10;
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        when(valueOperations.increment(keyCaptor.capture())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        repository.incrementWindowCounter(clientId, windowSeconds);

        String capturedKey = keyCaptor.getValue();
        assertTrue(capturedKey.startsWith("rate:client:client123:window:"));
    }

    @Test
    @DisplayName("Should format monthly key correctly")
    void shouldFormatMonthlyKeyCorrectly() {
        String clientId = "client123";
        String yearMonth = "2024-01";
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        when(valueOperations.increment(keyCaptor.capture())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        repository.incrementMonthlyCounter(clientId, yearMonth);

        String capturedKey = keyCaptor.getValue();
        assertEquals("rate:client:client123:monthly:2024-01", capturedKey);
    }

    @Test
    @DisplayName("Should format global window key correctly")
    void shouldFormatGlobalWindowKeyCorrectly() {
        int windowSeconds = 60;
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        when(valueOperations.increment(keyCaptor.capture())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        repository.incrementGlobalWindow(windowSeconds);

        String capturedKey = keyCaptor.getValue();
        assertTrue(capturedKey.startsWith("rate:global:window:"));
    }
}
