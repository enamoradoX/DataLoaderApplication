package org.mytestproject.dataloader.services;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.configurations.KafkaConfiguration;
import org.mytestproject.dataloader.models.EmployeeRecordData;
import org.mytestproject.dataloader.models.SkipEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;

/**
 * Single place that publishes skip events to Kafka, shared by both load strategies
 * (the Spring Batch EmployeeSkipListener and the legacy DataLoaderService) so that
 * either path triggers the downstream SkipEventConsumer -> email notification flow.
 */
@Service
@Slf4j
public class SkipEventPublisher {

    private final KafkaTemplate<String, SkipEvent> kafkaTemplate;

    public SkipEventPublisher(KafkaTemplate<String, SkipEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a skip event with no row payload (used when the row could not be parsed
     * into columns, e.g. READ-phase failures).
     */
    public void publish(String phase, String recordId, String errorMessage) {
        publish(phase, recordId, errorMessage, null);
    }

    /**
     * Publishes a skip event asynchronously, carrying the original row when available so the
     * downstream email/UI can show and reprocess it. Uses recordId as the partition key to keep
     * events for the same record ordered. Fail-silent: a broker problem is logged, never
     * rethrown, so a Kafka outage can't halt a running load.
     */
    public void publish(String phase, String recordId, String errorMessage, EmployeeRecordData data) {
        SkipEvent event = new SkipEvent(phase, recordId, errorMessage, Instant.now(), data);

        kafkaTemplate.send(KafkaConfiguration.KAFKA_TOPIC, recordId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send skip event to Kafka for Record ID: {}", recordId, ex);
                    } else {
                        log.debug("Successfully sent skip event to Kafka for Record ID: {} on topic {}",
                                recordId, KafkaConfiguration.KAFKA_TOPIC);
                    }
                });
    }
}
