package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.repository.ClientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@TestPropertySource(properties = {
        "app.api-key.prefix=LM",
        "app.api-key.key-bytes=32",
        "app.api-key.max-attempts=10",
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
class ApiKeyGeneratorServiceTest {

    @Autowired
    private ApiKeyGeneratorService service;

    @Autowired
    private ClientRepository clientRepository;

    @Test
    @DisplayName("Should generate secure API key with prefix")
    void shouldGenerateSecureApiKeyWithPrefix() {
        String apiKey = service.generateSecureApiKey();

        assertThat(apiKey).isNotNull();
        assertThat(apiKey).startsWith("LM-");
        assertThat(apiKey.length()).isGreaterThan(10);
    }

    @Test
    @DisplayName("Should generate unique API keys")
    void shouldGenerateUniqueApiKeys() {
        String apiKey1 = service.generateSecureApiKey();
        String apiKey2 = service.generateSecureApiKey();

        assertThat(apiKey1).isNotEqualTo(apiKey2);
    }

    @Test
    @DisplayName("Should generate segmented API key")
    void shouldGenerateSegmentedApiKey() {
        String apiKey = service.generateSegmentedApiKey();

        assertThat(apiKey).isNotNull();
        assertThat(apiKey).isNotEmpty();
        assertThat(apiKey).startsWith("LM");
    }

    @Test
    @DisplayName("Should generate different segmented keys")
    void shouldGenerateDifferentSegmentedKeys() {
        String apiKey1 = service.generateSegmentedApiKey();
        String apiKey2 = service.generateSegmentedApiKey();

        assertThat(apiKey1).isNotEqualTo(apiKey2);
    }
}

