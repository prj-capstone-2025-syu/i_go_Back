package com.example.demo.exception;

/**
 * Custom exception thrown when a location cannot be found or geocoded
 */
public class LocationNotFoundException extends RuntimeException {
    public LocationNotFoundException(String message) {
        super(message);
    }

    public LocationNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
