package com.ameen.healthcare.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Payload for creating or updating a {@link com.ameen.healthcare.entity.Patient} profile.
 * The authenticated user's ID is taken from the JWT, not this body.
 */
public record PatientProfileRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        String lastName,

        LocalDate dateOfBirth,

        @Size(max = 20)
        String phone,

        String address
) {}
