package com.ameen.healthcare.exception;

/** Thrown when creating an entity that violates a uniqueness rule. Maps to 409. */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
