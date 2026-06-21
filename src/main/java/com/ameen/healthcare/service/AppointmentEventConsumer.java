package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.event.AppointmentCancelledEvent;
import com.ameen.healthcare.dto.event.AppointmentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class AppointmentEventConsumer {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentEventConsumer.class);

    private final NotificationService notificationService;

    public AppointmentEventConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

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
            logger.info("Notifications dispatched for AppointmentCreatedEvent: appointmentId={}",
                    event.getAppointmentId());
        } catch (Exception e) {
            logger.error("Error processing AppointmentCreatedEvent: appointmentId={}",
                    event.getAppointmentId(), e);
        }
    }

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
            logger.info("Notifications dispatched for AppointmentCancelledEvent: appointmentId={}",
                    event.getAppointmentId());
        } catch (Exception e) {
            logger.error("Error processing AppointmentCancelledEvent: appointmentId={}",
                    event.getAppointmentId(), e);
        }
    }
}
