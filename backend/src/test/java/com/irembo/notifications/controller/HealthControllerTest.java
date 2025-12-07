package com.irembo.notifications.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.irembo.notifications.filter.APIKeyAuthFilter;
import com.irembo.notifications.filter.AdminRateLimiterFilter;
import com.irembo.notifications.filter.RateLimiterFilter;
import com.irembo.notifications.filter.SignatureValidationFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = HealthController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {APIKeyAuthFilter.class, AdminRateLimiterFilter.class, RateLimiterFilter.class, SignatureValidationFilter.class}
        )
)
@AutoConfigureMockMvc(addFilters = false)
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private APIKeyAuthFilter apiKeyAuthFilter;

    @MockBean
    private AdminRateLimiterFilter adminRateLimiterFilter;

    @MockBean
    private RateLimiterFilter rateLimiterFilter;

    @MockBean
    private SignatureValidationFilter signatureValidationFilter;

    @Test
    @DisplayName("Should return health status UP")
    void shouldReturnHealthStatusUp() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("notifications-api"));
    }

    @Test
    @DisplayName("Should return valid JSON response")
    void shouldReturnValidJsonResponse() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$.status").exists())
                .andExpect(jsonPath("$.service").exists());
    }
}
