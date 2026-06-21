package com.ameen.healthcare.dto.event;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Published when an appointment is successfully booked.
 * Triggers email notification to the patient via the Kafka consumer.
 */
public class AppointmentCreatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long appointmentId;
    private Long patientId;
    private Long doctorId;
    private LocalDateTime appointmentDateTime;
    private String doctorName;
    private String patientEmail;
    private String patientPhoneNumber;
    private String reason;

    // Constructors
    public AppointmentCreatedEvent() {
    }

    public AppointmentCreatedEvent(Long appointmentId, Long patientId, Long doctorId,
                                   LocalDateTime appointmentDateTime, String doctorName,
                                   String patientEmail, String patientPhoneNumber, String reason) {
        this.appointmentId = appointmentId;
        this.patientId = patientId;
        this.doctorId = doctorId;
        this.appointmentDateTime = appointmentDateTime;
        this.doctorName = doctorName;
        this.patientEmail = patientEmail;
        this.patientPhoneNumber = patientPhoneNumber;
        this.reason = reason;
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

    public String getPatientPhoneNumber() {
        return patientPhoneNumber;
    }

    public String getReason() {
        return reason;
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

    public void setPatientPhoneNumber(String patientPhoneNumber) {
        this.patientPhoneNumber = patientPhoneNumber;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    @Override
    public String toString() {
        return "AppointmentCreatedEvent{" +
                "appointmentId=" + appointmentId +
                ", patientId=" + patientId +
                ", doctorId=" + doctorId +
                ", appointmentDateTime=" + appointmentDateTime +
                ", doctorName='" + doctorName + '\'' +
                ", patientEmail='" + patientEmail + '\'' +
                ", patientPhoneNumber='" + patientPhoneNumber + '\'' +
                ", reason='" + reason + '\'' +
                '}';
    }
}
