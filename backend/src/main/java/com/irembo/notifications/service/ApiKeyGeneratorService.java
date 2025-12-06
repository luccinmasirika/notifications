package com.irembo.notifications.service;

import com.irembo.notifications.infra.db.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ApiKeyGeneratorService {

    private static final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.api-key.prefix:LM}")
    private String prefix;

    @Value("${app.api-key.key-bytes:32}")
    private int keyBytes;

    @Value("${app.api-key.max-attempts:10}")
    private int maxAttempts;

    private final ApiKeyHashService apiKeyHashService;
    private final ClientRepository clientRepository;

    public ApiKeyGeneratorService(
            ApiKeyHashService apiKeyHashService,
            ClientRepository clientRepository) {
        this.apiKeyHashService = apiKeyHashService;
        this.clientRepository = clientRepository;
    }

    public String generateSecureApiKey() {
        byte[] randomBytes = new byte[keyBytes];
        secureRandom.nextBytes(randomBytes);

        String encoded = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);

        return prefix + "-" + encoded;
    }

    public String generateUniqueApiKey() {
        String apiKey;
        String hashedKey;
        int attempts = 0;

        do {
            apiKey = generateSecureApiKey();
            hashedKey = apiKeyHashService.hashApiKey(apiKey);
            attempts++;

            if (attempts >= maxAttempts) {
                throw new RuntimeException("Failed to generate unique API key after " + maxAttempts + " attempts");
            }
        } while (clientRepository.existsByApiKeyHash(hashedKey));

        return apiKey;
    }

    public String generateSegmentedApiKey() {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);

        StringBuilder apiKey = new StringBuilder(prefix);
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        for (int i = 0; i < randomBytes.length; i++) {
            int value = randomBytes[i] & 0xFF;
            apiKey.append(chars.charAt(value % chars.length()));

            if (i == 2 || i == 8 || i == 14) {
                apiKey.append("-");
            }
        }

        return apiKey.toString();
    }
}
