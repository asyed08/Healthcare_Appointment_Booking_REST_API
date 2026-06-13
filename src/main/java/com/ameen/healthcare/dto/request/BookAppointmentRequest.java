package com.ameen.healthcare.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Payload to book an appointment for an available slot.
 * The patient identity comes from the JWT, not this body.
 */
public record BookAppointmentRequest(

        @NotNull(message = "Slot ID is required")
        Long slotId,

        String notes
) {}
