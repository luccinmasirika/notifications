package com.irembo.notifications.validation;

import com.irembo.notifications.model.dto.ClientLimitRequest;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ThresholdsValidatorTest {

    private ThresholdsValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @Mock
    private ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext nodeBuilder;

    @BeforeEach
    void setUp() {
        validator = new ThresholdsValidator();
        validator.initialize(null);
    }

    @Test
    @DisplayName("Should return true when request is null")
    void shouldReturnTrueWhenRequestIsNull() {
        boolean result = validator.isValid(null, context);
        assertThat(result).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("Should return true when both thresholds are null")
    void shouldReturnTrueWhenBothThresholdsAreNull() {
        ClientLimitRequest request = new ClientLimitRequest(60, 100, 10000, null, null);
        boolean result = validator.isValid(request, context);
        assertThat(result).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("Should return true when soft threshold is null")
    void shouldReturnTrueWhenSoftThresholdIsNull() {
        ClientLimitRequest request = new ClientLimitRequest(60, 100, 10000, null, 1.0);
        boolean result = validator.isValid(request, context);
        assertThat(result).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("Should return true when hard threshold is null")
    void shouldReturnTrueWhenHardThresholdIsNull() {
        ClientLimitRequest request = new ClientLimitRequest(60, 100, 10000, 0.8, null);
        boolean result = validator.isValid(request, context);
        assertThat(result).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("Should return true when soft threshold is less than hard threshold")
    void shouldReturnTrueWhenSoftThresholdIsLessThanHardThreshold() {
        ClientLimitRequest request = new ClientLimitRequest(60, 100, 10000, 0.8, 1.0);
        boolean result = validator.isValid(request, context);
        assertThat(result).isTrue();
        verifyNoInteractions(context);
    }

    @Test
    @DisplayName("Should return false when soft threshold equals hard threshold")
    void shouldReturnFalseWhenSoftThresholdEqualsHardThreshold() {
        ClientLimitRequest request = new ClientLimitRequest(60, 100, 10000, 0.8, 0.8);
        
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addConstraintViolation()).thenReturn(context);
        
        boolean result = validator.isValid(request, context);
        
        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
            "Soft throttle threshold (0.8) must be less than hard reject threshold (0.8)");
        verify(violationBuilder).addConstraintViolation();
    }

    @Test
    @DisplayName("Should return false when soft threshold is greater than hard threshold")
    void shouldReturnFalseWhenSoftThresholdIsGreaterThanHardThreshold() {
        ClientLimitRequest request = new ClientLimitRequest(60, 100, 10000, 0.9, 0.8);
        
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(violationBuilder);
        when(violationBuilder.addConstraintViolation()).thenReturn(context);
        
        boolean result = validator.isValid(request, context);
        
        assertThat(result).isFalse();
        verify(context).disableDefaultConstraintViolation();
        verify(context).buildConstraintViolationWithTemplate(
            "Soft throttle threshold (0.9) must be less than hard reject threshold (0.8)");
        verify(violationBuilder).addConstraintViolation();
    }
}

