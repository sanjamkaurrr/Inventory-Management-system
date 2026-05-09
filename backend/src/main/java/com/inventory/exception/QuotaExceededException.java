package com.inventory.exception;

/** Thrown when a user exceeds their tier's daily/monthly quota. */
public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String message) {
        super(message);
    }
}
