package com.ameen.healthcare.enums;

/**
 * Lifecycle states of an appointment.
 *
 * <p>Allowed transitions:
 * <pre>
 *   PENDING → CONFIRMED → COMPLETED
 *   PENDING → CANCELLED
 *   CONFIRMED → CANCELLED
 * </pre>
 */
public enum AppointmentStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED
}

