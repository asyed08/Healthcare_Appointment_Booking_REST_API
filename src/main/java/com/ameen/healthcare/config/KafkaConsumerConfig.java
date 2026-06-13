package com.ameen.healthcare.config;

import com.ameen.healthcare.dto.event.AppointmentCancelledEvent;
import com.ameen.healthcare.dto.event.AppointmentCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer configuration for listening to appointment events.
 * Uses manual acknowledgment to ensure messages are processed successfully
 * before marking them as consumed.
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:appointment-service-group}")
    private String groupId;

    /**
     * Configure consumer properties for event deserialization.
     */
    private Map<String, Object> consumerConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AppointmentCreatedEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);  // manual commit
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        return props;
    }

    /**
     * Consumer factory for AppointmentCreatedEvent.
     */
    @Bean(name = "appointmentCreatedEventConsumerFactory")
    public DefaultKafkaConsumerFactory<String, AppointmentCreatedEvent> appointmentCreatedEventConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                consumerConfig(),
                new StringDeserializer(),
                new JsonDeserializer<>(AppointmentCreatedEvent.class)
        );
    }

    /**
     * Consumer factory for AppointmentCancelledEvent.
     */
    @Bean(name = "appointmentCancelledEventConsumerFactory")
    public DefaultKafkaConsumerFactory<String, AppointmentCancelledEvent> appointmentCancelledEventConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                consumerConfig(),
                new StringDeserializer(),
                new JsonDeserializer<>(AppointmentCancelledEvent.class)
        );
    }
}
