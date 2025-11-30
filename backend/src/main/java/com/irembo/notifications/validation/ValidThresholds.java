package com.irembo.notifications.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates that softThrottleThreshold is less than hardRejectThreshold.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ThresholdsValidator.class)
@Documented
public @interface ValidThresholds {
    String message() default "Soft throttle threshold must be less than hard reject threshold";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
