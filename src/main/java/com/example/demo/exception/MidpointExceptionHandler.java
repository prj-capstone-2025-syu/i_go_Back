package com.example.demo.exception;

import com.example.demo.dto.midpoint.MidpointResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

/**
 * Global exception handler for handling LocationNotFoundException
 * and other exceptions in the midpoint calculation API
 */
@Slf4j
@ControllerAdvice
@RestController
public class MidpointExceptionHandler {

    /**
     * Handles LocationNotFoundException and returns appropriate error response
     */
    @ExceptionHandler(LocationNotFoundException.class)
    public ResponseEntity<MidpointResponse> handleLocationNotFoundException(LocationNotFoundException e) {
        log.error("Location not found: {}", e.getMessage());

        MidpointResponse response = MidpointResponse.builder()
                .success(false)
                .message(e.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }
}
