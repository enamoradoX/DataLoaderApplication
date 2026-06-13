package org.mytestproject.dataloader.configurations;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.mytestproject.dataloader.models.SkipEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfiguration {

    public static final String KAFKA_TOPIC = "employee-skip-events-topic";

    @Value("${spring.kafka.consumer.group-id}")
    private String consumerGroupId;

    @Value("${spring.kafka.consumer.auto-offset-reset:earliest}")
    private String autoOffsetReset;

    /**
     * Programmatically defines the Kafka topic.
     * Spring Boot's KafkaAdmin will see this bean and automatically
     * create the topic on your Docker broker if it's missing.
     */
    @Bean
    public NewTopic employeeSkipEventsTopic() {
        return TopicBuilder.name(KAFKA_TOPIC)
                .partitions(3)       // Distributes load; allows up to 3 parallel consumers later
                .replicas(1)         // Set to 1 because your local Docker setup has exactly 1 broker
                .build();
    }

    @Bean
    public ProducerFactory<String, SkipEvent> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();

        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");

        // Pass the modern JacksonJsonSerializer to the Factory constructor
        return new DefaultKafkaProducerFactory<>(
                configProps,
                new StringSerializer(),
                new JacksonJsonSerializer<SkipEvent>()
        );
    }

    /**
     * Explicitly defines the KafkaTemplate bean for your SkipEvent records.
     * While Spring Boot tries to autoconfigure a generic template, explicitly declaring it
     * gives you precise type safety control for your Java 25 records.
     */
    @Bean
    public KafkaTemplate<String, SkipEvent> kafkaTemplate(ProducerFactory<String, SkipEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Consumer side: deserializes JSON messages from the topic back into SkipEvent records.
     * Mirrors the producer factory above.
     */
    @Bean
    public ConsumerFactory<String, SkipEvent> consumerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configProps.put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroupId);
        configProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);

        // Force every message to deserialize to SkipEvent and ignore any __TypeId__ header
        // the producer may have stamped on the record, so the consumer is self-contained.
        JacksonJsonDeserializer<SkipEvent> valueDeserializer = new JacksonJsonDeserializer<>(SkipEvent.class);
        valueDeserializer.ignoreTypeHeaders();
        valueDeserializer.addTrustedPackages("org.mytestproject.dataloader.models");

        return new DefaultKafkaConsumerFactory<>(
                configProps,
                new StringDeserializer(),
                valueDeserializer
        );
    }

    /**
     * The container factory referenced by @KafkaListener(containerFactory = "skipEventKafkaListenerContainerFactory").
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, SkipEvent>
        skipEventKafkaListenerContainerFactory(ConsumerFactory<String, SkipEvent> consumerFactory) {

            ConcurrentKafkaListenerContainerFactory<String, SkipEvent> factory =
                    new ConcurrentKafkaListenerContainerFactory<>();
            factory.setConsumerFactory(consumerFactory);

            return factory;
        }
}