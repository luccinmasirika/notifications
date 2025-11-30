package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.infra.redis.RedisCounterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientLimitRepository clientLimitRepository;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @Mock
    private RedisCounterRepository redisCounter;

    @Mock
    private ApiKeyHashService apiKeyHashService;

    @InjectMocks
    private AdminService adminService;

    private Client testClient;
    private ClientLimit testLimit;

    @BeforeEach
    void setUp() {
        testClient = new Client();
        testClient.setId(1L);
        // Use ApiKeyHashService to hash the API key (V8 migration - no plain text storage)
        ApiKeyHashService hashService = new ApiKeyHashService();
        testClient.setApiKeyHash(hashService.hashApiKey("test-api-key-123456"));
        testClient.setName("Test Client");
        testClient.setPriority(5);
        testClient.setActive(true);

        testLimit = new ClientLimit();
        testLimit.setId(1L);
        testLimit.setClientId(1L);
        testLimit.setWindowSizeSeconds(10);
        testLimit.setMaxRequestsPerWindow(100);
        testLimit.setMonthlyQuota(10000);
    }

    @Test
    void testUpdateClient_Success() {
        when(clientRepository.save(any(Client.class))).thenReturn(testClient);
        when(cacheManager.getCache("clientConfigs")).thenReturn(cache);
        doNothing().when(cache).clear();

        Client result = adminService.updateClient(1L, testClient);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("Test Client", result.getName());

        verify(clientRepository).save(testClient);
        verify(cacheManager).getCache("clientConfigs");
        verify(cache).clear();
    }

    @Test
    void testCreateOrUpdateClientLimit_NewLimit() {
        when(clientLimitRepository.findByClientId(1L)).thenReturn(Optional.empty());
        when(clientLimitRepository.save(any(ClientLimit.class))).thenReturn(testLimit);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(cacheManager.getCache("clientConfigs")).thenReturn(cache);
        doNothing().when(cache).clear();

        ClientLimit result = adminService.createOrUpdateClientLimit(1L, testLimit);

        assertNotNull(result);
        assertEquals(1L, result.getClientId());
        assertEquals(100, result.getMaxRequestsPerWindow());

        verify(clientLimitRepository).findByClientId(1L);
        verify(clientLimitRepository).save(testLimit);
        verify(cache).clear();
    }

    @Test
    void testCreateOrUpdateClientLimit_UpdateExisting() {
        ClientLimit existingLimit = new ClientLimit();
        existingLimit.setId(2L);
        existingLimit.setClientId(1L);

        when(clientLimitRepository.findByClientId(1L)).thenReturn(Optional.of(existingLimit));
        when(clientLimitRepository.save(any(ClientLimit.class))).thenReturn(testLimit);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(cacheManager.getCache("clientConfigs")).thenReturn(cache);
        doNothing().when(cache).clear();

        ClientLimit result = adminService.createOrUpdateClientLimit(1L, testLimit);

        assertNotNull(result);
        // Verify that the existing limit ID was set
        assertEquals(2L, testLimit.getId());

        verify(clientLimitRepository).findByClientId(1L);
        verify(clientLimitRepository).save(testLimit);
        verify(cache).clear();
    }

    @Test
    void testUpdateClientLimit_Success() {
        when(clientLimitRepository.save(any(ClientLimit.class))).thenReturn(testLimit);
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(cacheManager.getCache("clientConfigs")).thenReturn(cache);
        doNothing().when(cache).clear();

        ClientLimit result = adminService.updateClientLimit(testLimit);

        assertNotNull(result);
        assertEquals(1L, result.getClientId());

        verify(clientLimitRepository).save(testLimit);
        verify(cache).clear();
    }

    @Test
    void testDeleteClientLimit_Success() {
        when(clientLimitRepository.findById(1L)).thenReturn(Optional.of(testLimit));
        when(clientRepository.findById(1L)).thenReturn(Optional.of(testClient));
        when(cacheManager.getCache("clientConfigs")).thenReturn(cache);
        doNothing().when(cache).clear();
        doNothing().when(clientLimitRepository).deleteById(1L);

        adminService.deleteClientLimit(1L);

        verify(clientLimitRepository).findById(1L);
        verify(clientLimitRepository).deleteById(1L);
        verify(cache).clear();
    }

    @Test
    void testDeleteClientLimit_NotFound() {
        when(clientLimitRepository.findById(999L)).thenReturn(Optional.empty());

        adminService.deleteClientLimit(999L);

        verify(clientLimitRepository).findById(999L);
        verify(clientLimitRepository, never()).deleteById(any());
        verify(cacheManager, never()).getCache(any());
    }

    @Test
    void testCacheEviction_NullCache() {
        when(clientRepository.save(any(Client.class))).thenReturn(testClient);
        when(cacheManager.getCache("clientConfigs")).thenReturn(null);

        // Should not throw exception even if cache is null
        assertDoesNotThrow(() -> adminService.updateClient(1L, testClient));

        verify(clientRepository).save(testClient);
    }

    @Test
    void testCacheEviction_CacheClearException() {
        when(clientRepository.save(any(Client.class))).thenReturn(testClient);
        when(cacheManager.getCache("clientConfigs")).thenReturn(cache);
        doThrow(new RuntimeException("Cache error")).when(cache).clear();

        // Should not throw exception even if cache clear fails
        assertDoesNotThrow(() -> adminService.updateClient(1L, testClient));

        verify(clientRepository).save(testClient);
        verify(cache).clear();
    }

    @Test
    void testUpdateClientLimit_ClientNotFound() {
        testLimit.setClientId(999L);

        when(clientLimitRepository.save(any(ClientLimit.class))).thenReturn(testLimit);
        when(clientRepository.findById(999L)).thenReturn(Optional.empty());

        ClientLimit result = adminService.updateClientLimit(testLimit);

        assertNotNull(result);
        // When client is not found, cache eviction is handled via @CacheEvict and
        // no direct interaction with the underlying Cache is required here.
    }
}
