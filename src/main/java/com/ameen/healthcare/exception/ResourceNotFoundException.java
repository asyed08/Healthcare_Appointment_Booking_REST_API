package com.ameen.healthcare.exception;

/** Thrown when a requested domain entity does not exist. Maps to 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
