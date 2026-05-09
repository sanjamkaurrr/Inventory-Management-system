package com.inventory.exception;

/** Thrown when seed/config data fails validation (duplicates, negatives, orphans). */
public class DataValidationException extends RuntimeException {
    public DataValidationException(String message) {
        super(message);
    }
}
