package org.mytestproject.dataloader.configurations;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.mytestproject.dataloader.models.SkipEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaConfiguration {

    public static final String KAFKA_TOPIC = "employee-skip-events-topic";

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
}