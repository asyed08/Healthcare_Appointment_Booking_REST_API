package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.event.AppointmentCancelledEvent;
import com.ameen.healthcare.dto.event.AppointmentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

/**
 * Consumes appointment lifecycle events from Kafka and dispatches
 * notifications via {@link NotificationService}.
 *
 * <p>Email delivery is handled by Spring's {@link org.springframework.mail.javamail.JavaMailSender}.
 * SMS and calendar integrations are structured stubs inside {@link NotificationService}
 * — see that class for wiring instructions (Twilio, Google Calendar, Microsoft Graph).
 *
 * <p>Each handler method is idempotent: if a downstream notification fails, the exception
 * is caught and logged so the Kafka offset is still committed and the event is not
 * replayed indefinitely. For at-least-once delivery guarantees, configure a dead-letter
 * topic in {@code KafkaConsumerConfig}.
 */
@Service
public class AppointmentEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentEventConsumer.class);

    private final NotificationService notificationService;

    public AppointmentEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Handles a newly created appointment.
     * Sends a confirmation email to the patient, alerts the doctor, and
     * creates a calendar entry — all via {@link NotificationService}.
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
            notificationService.sendBookingConfirmationToPatient(event);
            notificationService.sendNewAppointmentAlertToDoctor(event);
            notificationService.createCalendarEntry(event);

            logger.info("Notifications dispatched for AppointmentCreatedEvent: appointmentId={}",
                    event.getAppointmentId());
        } catch (Exception e) {
            logger.error("Error processing AppointmentCreatedEvent: appointmentId={}",
                    event.getAppointmentId(), e);
        }
    }

    /**
     * Handles a cancelled appointment.
     * Sends a cancellation email to the patient, alerts the doctor, and
     * removes the calendar entry — all via {@link NotificationService}.
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
            notificationService.sendCancellationConfirmationToPatient(event);
            notificationService.sendCancellationAlertToDoctor(event);
            notificationService.deleteCalendarEntry(event);

            logger.info("Notifications dispatched for AppointmentCancelledEvent: appointmentId={}",
                    event.getAppointmentId());
        } catch (Exception e) {
            logger.error("Error processing AppointmentCancelledEvent: appointmentId={}",
                    event.getAppointmentId(), e);
        }
    }
}
