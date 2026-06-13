package com.ameen.healthcare.dto.response;

import com.ameen.healthcare.entity.Patient;

import java.time.LocalDate;

/**
 * Read model for a patient profile.
 */
public record PatientProfileResponse(
        Long id,
        Long userId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String phone,
        String address
) {
    public static PatientProfileResponse from(Patient p) {
        return new PatientProfileResponse(
                p.getId(),
                p.getUser().getId(),
                p.getFirstName(),
                p.getLastName(),
                p.getDateOfBirth(),
                p.getPhone(),
                p.getAddress()
        );
    }
}
