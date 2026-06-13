package com.ameen.healthcare.config;

import com.ameen.healthcare.dto.event.AppointmentCancelledEvent;
import com.ameen.healthcare.dto.event.AppointmentCreatedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer configuration for publishing appointment events.
 * Supports both local development (embedded broker) and production (Confluent Cloud).
 */
@Configuration
@EnableKafka
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * KafkaTemplate for AppointmentCreatedEvent.
     * Spring manages serialization via JsonSerializer.
     */
    @Bean(name = "appointmentCreatedEventKafkaTemplate")
    public KafkaTemplate<String, AppointmentCreatedEvent> appointmentCreatedEventKafkaTemplate() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerConfig()));
    }

    /**
     * KafkaTemplate for AppointmentCancelledEvent.
     */
    @Bean(name = "appointmentCancelledEventKafkaTemplate")
    public KafkaTemplate<String, AppointmentCancelledEvent> appointmentCancelledEventKafkaTemplate() {
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerConfig()));
    }

    private Map<String, Object> producerConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");  // wait for leader + replicas
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);  // idempotent producer
        return props;
    }
}
