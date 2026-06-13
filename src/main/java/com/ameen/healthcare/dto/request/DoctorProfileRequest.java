package com.ameen.healthcare.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating or updating a {@link com.ameen.healthcare.entity.Doctor} profile.
 * The authenticated user's ID is taken from the JWT, not this body.
 */
public record DoctorProfileRequest(

        @NotBlank(message = "First name is required")
        @Size(max = 100)
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100)
        String lastName,

        @NotBlank(message = "Specialization is required")
        @Size(max = 150)
        String specialization,

        @NotBlank(message = "License number is required")
        @Size(max = 100)
        String licenseNumber,

        @Size(max = 20)
        String phone,

        String bio
) {}
