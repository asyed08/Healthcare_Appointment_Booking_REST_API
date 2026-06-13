package com.ameen.healthcare.dto.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Published when an appointment is cancelled.
 * Triggers cancellation notification workflows.
 */
public class AppointmentCancelledEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long appointmentId;
    private Long patientId;
    private Long doctorId;
    private LocalDateTime appointmentDateTime;
    private String doctorName;
    private String patientEmail;
    private String cancellationReason;
    private LocalDateTime cancelledAt;

    // Constructors
    public AppointmentCancelledEvent() {
    }

    public AppointmentCancelledEvent(Long appointmentId, Long patientId, Long doctorId,
                                     LocalDateTime appointmentDateTime, String doctorName,
                                     String patientEmail, String cancellationReason, LocalDateTime cancelledAt) {
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.appointmentDateTime = appointmentDateTime;
        this.doctorName = doctorName;
        this.patientEmail = patientEmail;
        this.cancellationReason = cancellationReason;
        this.cancelledAt = cancelledAt;
    }

    // Getters
    public Long getAppointmentId() {
        return appointmentId;
    }

    public Long getPatientId() {
        return patientId;
    }

    public Long getDoctorId() {
        return doctorId;
    }

    public LocalDateTime getAppointmentDateTime() {
        return appointmentDateTime;
    }

    public String getDoctorName() {
        return doctorName;
    }

    public String getPatientEmail() {
        return patientEmail;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    // Setters
    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    public void setDoctorId(Long doctorId) {
        this.doctorId = doctorId;
    }

    public void setAppointmentDateTime(LocalDateTime appointmentDateTime) {
        this.appointmentDateTime = appointmentDateTime;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    public void setPatientEmail(String patientEmail) {
        this.patientEmail = patientEmail;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    @Override
    public String toString() {
        return "AppointmentCancelledEvent{" +
                "appointmentId=" + appointmentId +
                ", patientId=" + patientId +
                ", doctorId=" + doctorId +
                ", appointmentDateTime=" + appointmentDateTime +
                ", doctorName='" + doctorName + '\'' +
                ", patientEmail='" + patientEmail + '\'' +
                ", cancellationReason='" + cancellationReason + '\'' +
                ", cancelledAt=" + cancelledAt +
                '}';
    }
}
