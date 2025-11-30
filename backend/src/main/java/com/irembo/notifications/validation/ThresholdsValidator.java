package com.irembo.notifications.validation;

import com.irembo.notifications.model.dto.ClientLimitRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Validator for threshold constraints.
 */
public class ThresholdsValidator implements ConstraintValidator<ValidThresholds, ClientLimitRequest> {

    @Override
    public boolean isValid(ClientLimitRequest request, ConstraintValidatorContext context) {
        if (request == null) {
            return true;
        }

        Double softThreshold = request.softThrottleThreshold();
        Double hardThreshold = request.hardRejectThreshold();

        // If both are present, soft must be less than hard
        if (softThreshold != null && hardThreshold != null) {
            if (softThreshold >= hardThreshold) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        "Soft throttle threshold (" + softThreshold + ") must be less than hard reject threshold (" + hardThreshold + ")"
                ).addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
