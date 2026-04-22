package org.example.orderservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.example.orderservice.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Global exception handler for the Order Service.
 * <p>
 * This class provides centralized exception handling for all REST controllers in the order service.
 * It catches specific exceptions, logs them appropriately, and returns standardized error responses
 * to clients. This ensures consistent error handling across the API.
 * <p>
 * Handled exceptions:
 * <ul>
 *   <li>IllegalArgumentException - for inventory validation failures</li>
 *   <li>MethodArgumentNotValidException - for input validation errors</li>
 *   <li>Exception - for any unexpected runtime errors</li>
 * </ul>
 *
 * @author Order Service Team
 * @version 1.0
 * @since 2024
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleInventoryException(IllegalArgumentException ex) {
        log.error("Inventory validation error: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("INVENTORY_INSUFFICIENT")
                .errorMessage(ex.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        log.error("Validation error: {}", ex.getMessage());

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));

        String errorMessage = "Validation failed: " + errors.toString();

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("VALIDATION_ERROR")
                .errorMessage(errorMessage)
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ErrorResponse error = ErrorResponse.builder()
                .errorCode("INTERNAL_SERVER_ERROR")
                .errorMessage("An unexpected error occurred in the system. Please try again later.")
                .timestamp(LocalDateTime.now())
                .build();

        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}