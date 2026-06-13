package com.ameen.healthcare.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a patient attempts to book a slot that is no longer AVAILABLE
 * (already booked, or taken by a concurrent request).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class SlotUnavailableException extends RuntimeException {
    public SlotUnavailableException(String message) {
        super(message);
    }
}
