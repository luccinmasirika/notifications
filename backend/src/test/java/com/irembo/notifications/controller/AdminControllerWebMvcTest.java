package com.irembo.notifications.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.filter.APIKeyAuthFilter;
import com.irembo.notifications.filter.AdminRateLimiterFilter;
import com.irembo.notifications.filter.ContentCachingFilter;
import com.irembo.notifications.filter.RateLimiterFilter;
import com.irembo.notifications.filter.SignatureValidationFilter;
import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.entity.SystemLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.infra.db.repository.SystemLimitRepository;
import com.irembo.notifications.model.dto.ClientDetailsResponse;
import com.irembo.notifications.model.dto.ClientDto;
import com.irembo.notifications.model.dto.SystemLimitRequest;
import com.irembo.notifications.service.AdminService;
import com.irembo.notifications.service.ApiKeyGeneratorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.context.annotation.FilterType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        APIKeyAuthFilter.class,
                        AdminRateLimiterFilter.class,
                        RateLimiterFilter.class,
                        SignatureValidationFilter.class,
                        ContentCachingFilter.class
                }
        )
)
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ClientRepository clientRepository;

    @MockBean
    private ClientLimitRepository clientLimitRepository;

    @MockBean
    private SystemLimitRepository systemLimitRepository;

    @MockBean
    private AdminService adminService;

    @MockBean
    private ApiKeyGeneratorService apiKeyGeneratorService;

    private Client buildClient() {
        Client client = new Client();
        client.setId(1L);
        client.setName("Test Client");
        client.setPriority(5);
        client.setActive(true);
        client.setStatus("ACTIVE");
        client.setAuthMethod("HMAC");
        return client;
    }

    @Test
    @DisplayName("Should return active clients page")
    void shouldReturnActiveClients() throws Exception {
        Page<Client> page = new PageImpl<>(List.of(buildClient()), PageRequest.of(0, 20), 1);
        given(clientRepository.findByActiveTrue(any())).willReturn(page);

        mockMvc.perform(get("/admin/clients/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Test Client"));
    }

    @Test
    @DisplayName("Should return client details")
    void shouldReturnClientDetails() throws Exception {
        ClientDetailsResponse details = new ClientDetailsResponse(
                ClientDto.fromClient(buildClient()),
                new ClientLimit(),
                new ClientDetailsResponse.WindowUsage(1, 100, 60, 1.0, 99, Instant.now(), false, false),
                new ClientDetailsResponse.MonthlyUsage(10, 1000, 1.0, 990, false, false),
                new ClientDetailsResponse.ClientStatus(true, false, false, "ok", Instant.now())
        );
        given(adminService.getClientDetails(1L)).willReturn(details);

        mockMvc.perform(get("/admin/clients/1/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.client.name").value("Test Client"));
    }

    @Test
    @DisplayName("Should return 404 when client details not found")
    void shouldHandleClientDetailsNotFound() throws Exception {
        given(adminService.getClientDetails(99L)).willThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/admin/clients/99/details"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("not found"));
    }

    @Test
    @DisplayName("Should generate API key")
    void shouldGenerateApiKey() throws Exception {
        given(apiKeyGeneratorService.generateUniqueApiKey()).willReturn("generated-key");

        mockMvc.perform(get("/admin/generate-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").value("generated-key"));
    }

    @Test
    @DisplayName("Should handle API key generation failure")
    void shouldHandleApiKeyGenerationFailure() throws Exception {
        given(apiKeyGeneratorService.generateUniqueApiKey()).willThrow(new RuntimeException("boom"));

        mockMvc.perform(get("/admin/generate-api-key"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Failed to generate API key"));
    }

    @Test
    @DisplayName("Should reject invalid status update")
    void shouldRejectInvalidStatusUpdate() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("status", "INVALID"));

        mockMvc.perform(put("/admin/clients/1/status")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid status. Must be ACTIVE, SUSPENDED, or REVOKED"));
    }

    @Test
    @DisplayName("Should update client status")
    void shouldUpdateClientStatus() throws Exception {
        Client client = buildClient();
        client.setStatus("SUSPENDED");
        doNothing().when(adminService).updateClientStatus(1L, "SUSPENDED");
        given(clientRepository.findById(1L)).willReturn(Optional.of(client));
        String body = objectMapper.writeValueAsString(Map.of("status", "SUSPENDED"));

        mockMvc.perform(put("/admin/clients/1/status")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUSPENDED"));
    }

    @Test
    @DisplayName("Should return system limits")
    void shouldReturnSystemLimits() throws Exception {
        SystemLimit limit = new SystemLimit();
        limit.setId(1L);
        limit.setName("global");
        given(systemLimitRepository.findAll()).willReturn(List.of(limit));

        mockMvc.perform(get("/admin/system-limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("global"));
    }

    @Test
    @DisplayName("Should create system limit when not existing")
    void shouldCreateSystemLimit() throws Exception {
        SystemLimitRequest request = new SystemLimitRequest("global", 60, 1000, true);
        SystemLimit saved = new SystemLimit();
        saved.setId(1L);
        saved.setName("global");
        given(systemLimitRepository.findByName("global")).willReturn(Optional.empty());
        given(systemLimitRepository.save(any(SystemLimit.class))).willReturn(saved);
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/admin/system-limits")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("global"));
    }

    @Test
    @DisplayName("Should update system limit when existing")
    void shouldUpdateSystemLimit() throws Exception {
        SystemLimitRequest request = new SystemLimitRequest("global", 120, 500, false);
        SystemLimit existing = new SystemLimit();
        existing.setId(1L);
        existing.setName("global");
        given(systemLimitRepository.findByName("global")).willReturn(Optional.of(existing));
        given(systemLimitRepository.save(any(SystemLimit.class))).willAnswer(inv -> inv.getArgument(0));
        String body = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/admin/system-limits")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.windowSizeSeconds").value(120));
    }

    @Test
    @DisplayName("Should handle status update for missing client")
    void shouldHandleStatusUpdateNotFound() throws Exception {
        doThrow(new IllegalArgumentException("Client not found")).when(adminService).updateClientStatus(99L, "ACTIVE");
        given(clientRepository.findById(99L)).willReturn(Optional.empty());
        String body = objectMapper.writeValueAsString(Map.of("status", "ACTIVE"));

        mockMvc.perform(put("/admin/clients/99/status")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }
}
