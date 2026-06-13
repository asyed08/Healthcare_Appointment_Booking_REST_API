package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.event.AppointmentCancelledEvent;
import com.ameen.healthcare.dto.event.AppointmentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes appointment lifecycle events from Kafka.
 * Triggers notification workflows (email, SMS, calendar integration).
 *
 * <p>In production, integrate with actual email/SMS services like SendGrid, Twilio, etc.
 * For now, uses stub logging and can be extended with real implementations.
 */
@Service
public class AppointmentEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentEventConsumer.class);

    /**
     * Listens to appointment.created topic.
     * Sends confirmation email to patient and notification to doctor.
     */
    @KafkaListener(
            topics = "appointment.created",
            groupId = "appointment-service-group",
            containerFactory = "appointmentCreatedEventListenerContainerFactory"
    )
    public void handleAppointmentCreated(AppointmentCreatedEvent event) {
        logger.info("Received AppointmentCreatedEvent: appointmentId={}, patientEmail={}, doctorName={}",
                event.getAppointmentId(), event.getPatientEmail(), event.getDoctorName());

        try {
            // [STUB] Send confirmation email to patient
            sendConfirmationEmailToPatient(event);

            // [STUB] Send notification to doctor
            sendNotificationToDoctor(event);

            // [STUB] Sync with calendar service (Google Calendar, Outlook, etc.)
            syncToCalendar(event);

            logger.info("Successfully processed AppointmentCreatedEvent: appointmentId={}", event.getAppointmentId());
        } catch (Exception e) {
            logger.error("Error processing AppointmentCreatedEvent: appointmentId={}", event.getAppointmentId(), e);
            // In production, implement retry logic (exponential backoff, dead-letter queue)
        }
    }

    /**
     * Listens to appointment.cancelled topic.
     * Sends cancellation confirmation to patient and notification to doctor.
     */
    @KafkaListener(
            topics = "appointment.cancelled",
            groupId = "appointment-service-group",
            containerFactory = "appointmentCancelledEventListenerContainerFactory"
    )
    public void handleAppointmentCancelled(AppointmentCancelledEvent event) {
        logger.info("Received AppointmentCancelledEvent: appointmentId={}, patientEmail={}, reason={}",
                event.getAppointmentId(), event.getPatientEmail(), event.getCancellationReason());

        try {
            // [STUB] Send cancellation email to patient
            sendCancellationEmailToPatient(event);

            // [STUB] Send cancellation notification to doctor
            sendCancellationNotificationToDoctor(event);

            // [STUB] Remove from calendar
            removeFromCalendar(event);

            logger.info("Successfully processed AppointmentCancelledEvent: appointmentId={}", event.getAppointmentId());
        } catch (Exception e) {
            logger.error("Error processing AppointmentCancelledEvent: appointmentId={}", event.getAppointmentId(), e);
        }
    }

    // ===== Stub Methods (Production: integrate with real services) =====

    private void sendConfirmationEmailToPatient(AppointmentCreatedEvent event) {
        logger.debug("[STUB] Sending confirmation email to patient: {} for appointment on {}",
                event.getPatientEmail(), event.getAppointmentDateTime());
        // TODO: Integrate with SendGrid, AWS SES, or similar
        // EmailService.sendConfirmationEmail(event.getPatientEmail(), event);
    }

    private void sendNotificationToDoctor(AppointmentCreatedEvent event) {
        logger.debug("[STUB] Sending appointment notification to doctor: {} for appointment on {}",
                event.getDoctorName(), event.getAppointmentDateTime());
        // TODO: Integrate with notification service (Twilio, Firebase Cloud Messaging, etc.)
    }

    private void syncToCalendar(AppointmentCreatedEvent event) {
        logger.debug("[STUB] Syncing appointment to calendar for patient: {}", event.getPatientEmail());
        // TODO: Integrate with Google Calendar API, Microsoft Graph, etc.
    }

    private void sendCancellationEmailToPatient(AppointmentCancelledEvent event) {
        logger.debug("[STUB] Sending cancellation email to patient: {} for cancelled appointment",
                event.getPatientEmail());
        // TODO: Integrate with email service
    }

    private void sendCancellationNotificationToDoctor(AppointmentCancelledEvent event) {
        logger.debug("[STUB] Sending cancellation notification to doctor: {}", event.getDoctorName());
        // TODO: Integrate with notification service
    }

    private void removeFromCalendar(AppointmentCancelledEvent event) {
        logger.debug("[STUB] Removing appointment from calendar for patient: {}", event.getPatientEmail());
        // TODO: Integrate with calendar service
    }
}
