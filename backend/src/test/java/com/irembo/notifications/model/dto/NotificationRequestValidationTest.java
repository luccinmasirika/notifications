package com.irembo.notifications.model.dto;

import com.irembo.notifications.model.enums.NotificationChannel;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        factory.close();
    }

    @Test
    void shouldFailWhenFieldsMissing() {
        NotificationRequest request = new NotificationRequest(null, " ", " ");

        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);

        assertEquals(3, violations.size());
    }

    @Test
    void shouldFailWhenMessageTooLong() {
        String longMessage = "a".repeat(501);
        NotificationRequest request = new NotificationRequest(NotificationChannel.SMS, "+250700000000", longMessage);

        Set<ConstraintViolation<NotificationRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("must not exceed 500"));
    }
}

