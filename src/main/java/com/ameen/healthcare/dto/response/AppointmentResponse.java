package com.ameen.healthcare.dto.response;

import com.ameen.healthcare.entity.Appointment;
import com.ameen.healthcare.enums.AppointmentStatus;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Read model for an appointment booking record.
 */
public record AppointmentResponse(
        Long id,
        Long slotId,
        Long patientId,
        Long doctorId,
        String doctorFirstName,
        String doctorLastName,
        String doctorSpecialization,
        LocalDate appointmentDate,
        LocalTime startTime,
        LocalTime endTime,
        AppointmentStatus status,
        String notes
) {
    public static AppointmentResponse from(Appointment a) {
        return new AppointmentResponse(
                a.getId(),
                a.getSlot() != null ? a.getSlot().getId() : null,
                a.getPatient().getId(),
                a.getDoctor().getId(),
                a.getDoctor().getFirstName(),
                a.getDoctor().getLastName(),
                a.getDoctor().getSpecialization(),
                a.getAppointmentDate(),
                a.getStartTime(),
                a.getEndTime(),
                a.getStatus(),
                a.getNotes()
        );
    }
}
