package com.ameen.healthcare.service;

import com.ameen.healthcare.dto.event.AppointmentCancelledEvent;
import com.ameen.healthcare.dto.event.AppointmentCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Publishes appointment lifecycle events to Kafka topics.
 * Ensures reliable event distribution for notification workflows.
 */
@Service
public class AppointmentEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(AppointmentEventPublisher.class);

    private static final String APPOINTMENT_CREATED_TOPIC = "appointment.created";
    private static final String APPOINTMENT_CANCELLED_TOPIC = "appointment.cancelled";

    private final KafkaTemplate<String, AppointmentCreatedEvent> appointmentCreatedEventKafkaTemplate;
    private final KafkaTemplate<String, AppointmentCancelledEvent> appointmentCancelledEventKafkaTemplate;

    public AppointmentEventPublisher(
            @Qualifier("appointmentCreatedEventKafkaTemplate")
            KafkaTemplate<String, AppointmentCreatedEvent> appointmentCreatedEventKafkaTemplate,
            @Qualifier("appointmentCancelledEventKafkaTemplate")
            KafkaTemplate<String, AppointmentCancelledEvent> appointmentCancelledEventKafkaTemplate) {
        this.appointmentCreatedEventKafkaTemplate = appointmentCreatedEventKafkaTemplate;
        this.appointmentCancelledEventKafkaTemplate = appointmentCancelledEventKafkaTemplate;
    }

    /**
     * Publishes an appointment created event to Kafka.
     * Partitioned by patientId for ordering guarantees within a patient's event stream.
     */
    public void publishAppointmentCreated(AppointmentCreatedEvent event) {
        String partitionKey = event.getPatientId().toString();
        Message<AppointmentCreatedEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, APPOINTMENT_CREATED_TOPIC)
                .setHeader("kafka_messageKey", partitionKey)
                .build();

        appointmentCreatedEventKafkaTemplate.send(message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        logger.info("AppointmentCreatedEvent published: appointmentId={}, partition={}, offset={}",
                                event.getAppointmentId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        logger.error("Failed to publish AppointmentCreatedEvent: appointmentId={}",
                                event.getAppointmentId(), ex);
                    }
                });
    }

    /**
     * Publishes an appointment cancelled event to Kafka.
     * Partitioned by patientId for ordering guarantees within a patient's event stream.
     */
    public void publishAppointmentCancelled(AppointmentCancelledEvent event) {
        String partitionKey = event.getPatientId().toString();
        Message<AppointmentCancelledEvent> message = MessageBuilder
                .withPayload(event)
                .setHeader(KafkaHeaders.TOPIC, APPOINTMENT_CANCELLED_TOPIC)
                .setHeader("kafka_messageKey", partitionKey)
                .build();

        appointmentCancelledEventKafkaTemplate.send(message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        logger.info("AppointmentCancelledEvent published: appointmentId={}, partition={}, offset={}",
                                event.getAppointmentId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        logger.error("Failed to publish AppointmentCancelledEvent: appointmentId={}",
                                event.getAppointmentId(), ex);
                    }
                });
    }
}
