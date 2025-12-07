package com.irembo.notifications.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irembo.notifications.infra.db.entity.Client;
import com.irembo.notifications.infra.db.entity.ClientLimit;
import com.irembo.notifications.infra.db.repository.ClientLimitRepository;
import com.irembo.notifications.infra.db.repository.ClientRepository;
import com.irembo.notifications.model.dto.ClientLimitRequest;
import com.irembo.notifications.model.dto.CreateClientRequest;
import com.irembo.notifications.model.dto.UpdateClientRequest;
import com.irembo.notifications.service.ApiKeyHashService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "admin", roles = {"ADMIN"})
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
class AdminControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ClientLimitRepository clientLimitRepository;

    private Client testClient;

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
    }

    @Test
    @DisplayName("Should get clients paginated")
    void shouldGetClientsPaginated() throws Exception {
        mockMvc.perform(get("/admin/clients/page")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(testClient.getId().intValue()))
                .andExpect(jsonPath("$.content[0].name").value("Test Client"));
    }

    @Test
    @DisplayName("Should get active clients")
    void shouldGetActiveClients() throws Exception {
        mockMvc.perform(get("/admin/clients/active")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Should get client by id")
    void shouldGetClientById() throws Exception {
        mockMvc.perform(get("/admin/clients/{id}", testClient.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testClient.getId().intValue()))
                .andExpect(jsonPath("$.name").value("Test Client"));
    }

    @Test
    @DisplayName("Should return 404 when client not found")
    void shouldReturn404WhenClientNotFound() throws Exception {
        mockMvc.perform(get("/admin/clients/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Client not found"));
    }

    @Test
    @DisplayName("Should create new client")
    void shouldCreateNewClient() throws Exception {
        CreateClientRequest request = new CreateClientRequest(
                null,
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
    @DisplayName("Should update client")
    void shouldUpdateClient() throws Exception {
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
    @DisplayName("Should update client limits")
    void shouldUpdateClientLimits() throws Exception {
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
    @DisplayName("Should delete client")
    void shouldDeleteClient() throws Exception {
        mockMvc.perform(delete("/admin/clients/{id}", testClient.getId())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Client deleted"));
    }

    @Test
    @DisplayName("Should get limits paginated")
    void shouldGetLimitsPaginated() throws Exception {
        ClientLimit limit = new ClientLimit();
        limit.setClientId(testClient.getId());
        limit.setWindowSizeSeconds(60);
        limit.setMaxRequestsPerWindow(100);
        limit.setMonthlyQuota(10000);
        clientLimitRepository.save(limit);

        mockMvc.perform(get("/admin/limits/page")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("Should generate API key")
    void shouldGenerateApiKey() throws Exception {
        mockMvc.perform(get("/admin/generate-api-key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKey").exists())
                .andExpect(jsonPath("$.message").value("API key generated successfully"));
    }
}

