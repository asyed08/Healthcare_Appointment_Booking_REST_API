package com.ameen.healthcare.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * Consistent error envelope returned by the global exception handler.
 *
 * @param timestamp when the error occurred
 * @param status    HTTP status code
 * @param error     short status reason phrase
 * @param message   human-readable detail
 * @param path      request URI that produced the error
 * @param fieldErrors per-field validation messages (may be {@code null})
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
}
