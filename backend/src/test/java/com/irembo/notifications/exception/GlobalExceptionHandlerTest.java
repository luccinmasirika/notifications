package com.irembo.notifications.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("Should handle MethodArgumentNotValidException")
    void shouldHandleMethodArgumentNotValidException() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        
        FieldError fieldError1 = new FieldError("object", "field1", "Error message 1");
        FieldError fieldError2 = new FieldError("object", "field2", "Error message 2");
        List<FieldError> fieldErrors = Arrays.asList(fieldError1, fieldError2);
        
        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getAllErrors()).thenReturn(new ArrayList<>(fieldErrors));
        
        ResponseEntity<Map<String, Object>> response = handler.handleValidationExceptions(ex);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Validation Failed");
        assertThat(body.get("status")).isEqualTo(400);
        assertThat(body.get("timestamp")).isNotNull();
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) body.get("errors");
        assertThat(errors).hasSize(2);
        assertThat(errors.get(0).get("field")).isEqualTo("field1");
        assertThat(errors.get(0).get("message")).isEqualTo("Error message 1");
        assertThat(errors.get(1).get("field")).isEqualTo("field2");
        assertThat(errors.get(1).get("message")).isEqualTo("Error message 2");
    }

    @Test
    @DisplayName("Should handle ConstraintViolationException")
    void shouldHandleConstraintViolationException() {
        ConstraintViolation<?> violation1 = mock(ConstraintViolation.class);
        ConstraintViolation<?> violation2 = mock(ConstraintViolation.class);
        Path path1 = mock(Path.class);
        Path path2 = mock(Path.class);
        
        when(violation1.getPropertyPath()).thenReturn(path1);
        when(violation1.getMessage()).thenReturn("Constraint violation 1");
        when(path1.toString()).thenReturn("field1");
        
        when(violation2.getPropertyPath()).thenReturn(path2);
        when(violation2.getMessage()).thenReturn("Constraint violation 2");
        when(path2.toString()).thenReturn("field2");
        
        Set<ConstraintViolation<?>> violations = new HashSet<>(Arrays.asList(violation1, violation2));
        ConstraintViolationException ex = new ConstraintViolationException("Validation failed", violations);
        
        ResponseEntity<Map<String, Object>> response = handler.handleConstraintViolationException(ex);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Validation Failed");
        assertThat(body.get("status")).isEqualTo(400);
        assertThat(body.get("timestamp")).isNotNull();
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> errors = (List<Map<String, String>>) body.get("errors");
        assertThat(errors).hasSize(2);
    }

    @Test
    @DisplayName("Should handle IllegalArgumentException")
    void shouldHandleIllegalArgumentException() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid argument message");
        
        ResponseEntity<Map<String, Object>> response = handler.handleIllegalArgumentException(ex);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Invalid Argument");
        assertThat(body.get("status")).isEqualTo(400);
        assertThat(body.get("message")).isEqualTo("Invalid argument message");
        assertThat(body.get("timestamp")).isNotNull();
    }

    @Test
    @DisplayName("Should handle generic Exception")
    void shouldHandleGenericException() {
        Exception ex = new RuntimeException("Unexpected error");
        
        ResponseEntity<Map<String, Object>> response = handler.handleGenericException(ex);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.get("error")).isEqualTo("Internal Server Error");
        assertThat(body.get("status")).isEqualTo(500);
        assertThat(body.get("message")).isEqualTo("An unexpected error occurred");
        assertThat(body.get("timestamp")).isNotNull();
    }
}

