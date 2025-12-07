package com.irembo.notifications.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.service.ApiKeyHashService;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.model.dto.ClientLimitRequest;
import com.irembo.notifications.model.dto.CreateClientRequest;
import com.irembo.notifications.model.dto.UpdateClientRequest;
import com.irembo.notifications.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.properties.hibernate.format_sql=false",
        "spring.jpa.properties.hibernate.jdbc.lob.non_contextual_creation=true",
        "spring.flyway.enabled=false",
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6379",
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.port=5672",
        "management.health.rabbit.enabled=false"
})
@Transactional
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientLimitRepository clientLimitRepository;

    @Autowired
    private com.irembo.notifications.infra.db.repository.SystemLimitRepository systemLimitRepository;

    @Autowired
    private AdminService adminService;

    private Client testClient;
    private ClientLimit testLimit;

    @BeforeEach
    void setUp() {
        clientLimitRepository.deleteAll();
        clientRepository.deleteAll();
        
        testClient = new Client();
        ApiKeyHashService hashService = new ApiKeyHashService();
        testClient.setApiKeyHash(hashService.hashApiKey("test-api-key-12345"));
        testClient.setName("Test Client");
        testClient.setPriority(5);
        testClient.setActive(true);
        testClient.setAuthMethod("HMAC");
        testClient.setStatus("ACTIVE");
        testClient = clientRepository.save(testClient);

        testLimit = new ClientLimit();
        testLimit.setClientId(testClient.getId());
        testLimit.setWindowSizeSeconds(10);
        testLimit.setMaxRequestsPerWindow(100);
        testLimit.setMonthlyQuota(10000);
        testLimit = clientLimitRepository.save(testLimit);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testGetAllClients() throws Exception {
        mockMvc.perform(get("/admin/clients/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(testClient.getId().intValue()))
                .andExpect(jsonPath("$.content[0].name").value("Test Client"))
                .andExpect(jsonPath("$.content[0].priority").value(5));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testGetClientById_Success() throws Exception {
        mockMvc.perform(get("/admin/clients/{id}", testClient.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testClient.getId().intValue()))
                .andExpect(jsonPath("$.name").value("Test Client"))
                .andExpect(jsonPath("$.priority").value(5));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testGetClientById_NotFound() throws Exception {
        mockMvc.perform(get("/admin/clients/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Client not found"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testCreateClient_Success() throws Exception {
        CreateClientRequest request = new CreateClientRequest(
                "new-api-key-123456",
                "New Client",
                10,
                true
        );

        mockMvc.perform(post("/admin/clients")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Client"))
                .andExpect(jsonPath("$.apiKey").exists())
                .andExpect(jsonPath("$.apiSecret").exists());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testCreateClient_ValidationError() throws Exception {
        CreateClientRequest request = new CreateClientRequest(
                "short",
                "New Client",
                10,
                true
        );

        mockMvc.perform(post("/admin/clients")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testUpdateClient_Success() throws Exception {
        UpdateClientRequest request = new UpdateClientRequest(
                null,
                "Updated Client",
                15,
                false
        );

        mockMvc.perform(put("/admin/clients/{id}", testClient.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Client"))
                .andExpect(jsonPath("$.priority").value(15))
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testUpdateClient_NotFound() throws Exception {
        UpdateClientRequest request = new UpdateClientRequest(
                null,
                "Updated Client",
                15,
                false
        );

        mockMvc.perform(put("/admin/clients/999")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Client not found"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testUpdateClientLimits_Success() throws Exception {
        ClientLimitRequest request = new ClientLimitRequest(
                60,
                200,
                20000,
                0.80,
                1.00
        );

        mockMvc.perform(put("/admin/clients/{id}/limits", testClient.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(testClient.getId().intValue()))
                .andExpect(jsonPath("$.maxRequestsPerWindow").value(200));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testUpdateClientLimits_ClientNotFound() throws Exception {
        ClientLimitRequest request = new ClientLimitRequest(
                60,
                200,
                20000,
                0.80,
                1.00
        );

        mockMvc.perform(put("/admin/clients/999/limits")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Client not found"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testUpdateClientLimits_ValidationError() throws Exception {
        ClientLimitRequest request = new ClientLimitRequest(
                0,
                200,
                20000,
                0.80,
                1.00
        );

        mockMvc.perform(put("/admin/clients/{id}/limits", testClient.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testDeleteClient_Success() throws Exception {
        mockMvc.perform(delete("/admin/clients/{id}", testClient.getId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Client deleted"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testDeleteClient_NotFound() throws Exception {
        mockMvc.perform(delete("/admin/clients/999")
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Client not found"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testGetAllLimits() throws Exception {
        mockMvc.perform(get("/admin/limits/page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].clientId").value(testClient.getId().intValue()))
                .andExpect(jsonPath("$.content[0].windowSizeSeconds").value(10));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testGetClientLimit_Success() throws Exception {
        mockMvc.perform(get("/admin/limits/client/{clientId}", testClient.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clientId").value(testClient.getId().intValue()))
                .andExpect(jsonPath("$.maxRequestsPerWindow").value(100));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testGetClientLimit_NotFound() throws Exception {
        mockMvc.perform(get("/admin/limits/client/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Client limit not found"));
    }

    @Test
    void testUnauthorizedAccess() throws Exception {
        // Remove @WithMockUser for this test to test unauthorized access
        mockMvc.perform(get("/admin/clients"))
                .andExpect(status().isUnauthorized());
    }
}
