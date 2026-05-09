package com.inventory.exception;

/** Thrown when a user exceeds their tier's rate limit (requests per second). */
public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}
