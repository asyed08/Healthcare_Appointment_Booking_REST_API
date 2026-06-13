package com.ameen.healthcare.dto.response;

import com.ameen.healthcare.entity.Doctor;

import java.util.List;

/**
 * Read model for a doctor's full profile, including availability windows.
 */
public record DoctorProfileResponse(
        Long id,
        Long userId,
        String firstName,
        String lastName,
        String specialization,
        String licenseNumber,
        String phone,
        String bio,
        List<AvailabilityResponse> availability
) {
    public static DoctorProfileResponse from(Doctor d) {
        List<AvailabilityResponse> avail = d.getAvailabilitySlots().stream()
                .map(AvailabilityResponse::from)
                .toList();
        return new DoctorProfileResponse(
                d.getId(),
                d.getUser().getId(),
                d.getFirstName(),
                d.getLastName(),
                d.getSpecialization(),
                d.getLicenseNumber(),
                d.getPhone(),
                d.getBio(),
                avail
        );
    }
}
