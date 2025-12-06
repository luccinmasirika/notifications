package com.irembo.notifications.exception;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        List<Map<String, String>> errors = new ArrayList<>();

        ex.getBindingResult().getAllErrors().forEach((error) -> {
            Map<String, String> errorDetail = new HashMap<>();
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();

            errorDetail.put("field", fieldName);
            errorDetail.put("message", errorMessage);

            errors.add(errorDetail);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Validation Failed");
        response.put("status", 400);
        response.put("errors", errors);
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolationException(ConstraintViolationException ex) {
        List<Map<String, String>> errors = ex.getConstraintViolations().stream()
                .map(violation -> {
                    Map<String, String> errorDetail = new HashMap<>();
                    errorDetail.put("field", violation.getPropertyPath().toString());
                    errorDetail.put("message", violation.getMessage());
                    return errorDetail;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Validation Failed");
        response.put("status", 400);
        response.put("errors", errors);
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgumentException(IllegalArgumentException ex) {
        logger.warn("Invalid argument: {}", ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Invalid Argument");
        response.put("status", 400);
        response.put("message", ex.getMessage());
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        logger.error("Unexpected error occurred", ex);

        Map<String, Object> response = new HashMap<>();
        response.put("error", "Internal Server Error");
        response.put("status", 500);
        response.put("message", "An unexpected error occurred");
        response.put("timestamp", Instant.now().toEpochMilli());

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
