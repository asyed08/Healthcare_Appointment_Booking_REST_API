package com.ameen.healthcare.config;

import com.ameen.healthcare.dto.event.AppointmentCancelledEvent;
import com.ameen.healthcare.dto.event.AppointmentCreatedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
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

    @Value("${kafka.security.protocol:PLAINTEXT}")
    private String securityProtocol;

    @Value("${kafka.sasl.mechanism:PLAIN}")
    private String saslMechanism;

    @Value("${kafka.sasl.jaas.config:}")
    private String saslJaasConfig;

    @Value("${spring.kafka.listener.auto-startup:true}")
    private boolean autoStartup;

    private Map<String, Object> consumerConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AppointmentCreatedEvent.class.getName());
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        if (!"PLAINTEXT".equalsIgnoreCase(securityProtocol)) {
            props.put("security.protocol", securityProtocol);
            props.put("sasl.mechanism", saslMechanism);
            if (!saslJaasConfig.isBlank()) {
                props.put("sasl.jaas.config", saslJaasConfig);
            }
        }
        return props;
    }

    @Bean(name = "appointmentCreatedEventConsumerFactory")
    public DefaultKafkaConsumerFactory<String, AppointmentCreatedEvent> appointmentCreatedEventConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                consumerConfig(),
                new StringDeserializer(),
                new JsonDeserializer<>(AppointmentCreatedEvent.class)
        );
    }

    @Bean(name = "appointmentCancelledEventConsumerFactory")
    public DefaultKafkaConsumerFactory<String, AppointmentCancelledEvent> appointmentCancelledEventConsumerFactory() {
        return new DefaultKafkaConsumerFactory<>(
                consumerConfig(),
                new StringDeserializer(),
                new JsonDeserializer<>(AppointmentCancelledEvent.class)
        );
    }

    @Bean(name = "appointmentCreatedEventListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, AppointmentCreatedEvent> appointmentCreatedEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, AppointmentCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(appointmentCreatedEventConsumerFactory());
        factory.setAutoStartup(autoStartup);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }

    @Bean(name = "appointmentCancelledEventListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, AppointmentCancelledEvent> appointmentCancelledEventListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, AppointmentCancelledEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(appointmentCancelledEventConsumerFactory());
        factory.setAutoStartup(autoStartup);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        return factory;
    }
}
