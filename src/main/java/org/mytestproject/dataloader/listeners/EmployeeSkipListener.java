package org.mytestproject.dataloader.listeners;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.entities.Employee;
import org.mytestproject.dataloader.models.EmployeeDto;
import org.mytestproject.dataloader.models.SkipEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import jakarta.validation.ConstraintViolationException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
@Slf4j
@RequiredArgsConstructor // Automatically injects the KafkaTemplate via constructor
public class EmployeeSkipListener implements SkipListener<EmployeeDto, Employee> {

    private static final Logger auditLogger = LoggerFactory.getLogger("auditLogger");
    private static final String KAFKA_TOPIC = "employee-skip-events-topic";
    private final KafkaTemplate<String, SkipEvent> kafkaTemplate;

    @Override
    public void onSkipInRead(Throwable t) {
        String errorMsg = t.getMessage();
        log.error("Skipped during READ: Formatting/Parsing failure -> {}", errorMsg);
        auditLogger.info("PHASE: READ | RECORD ID: UNKNOWN | ERROR: {}", errorMsg);

        publishToKafka("READ", "UNKNOWN", errorMsg);
    }

    @Override
    public void onSkipInProcess(EmployeeDto item, Throwable t) {
        String recordId = (item != null) ? String.valueOf(item.id()) : "UNKNOWN";

        // Leveraging Java 21+ pattern matching for instanceof
        if (t.getCause() instanceof ConstraintViolationException violationException) {
            violationException.getConstraintViolations().forEach(violation -> {
                String errorMsg = String.format("Field '%s' %s", violation.getPropertyPath(), violation.getMessage());

                log.warn("Skipped Row [ID: {}]: {}", recordId, errorMsg);
                auditLogger.info("PHASE: PROCESS_VALIDATION | RECORD ID: {} | ERROR: {}", recordId, errorMsg);

                publishToKafka("PROCESS_VALIDATION", recordId, errorMsg);
            });
        } else {
            String errorMsg = t.getMessage();
            log.warn("Skipped during PROCESS [ID: {}]: Reason -> {}", recordId, errorMsg);
            auditLogger.info("PHASE: PROCESS | RECORD ID: {} | ERROR: {}", recordId, errorMsg);

            publishToKafka("PROCESS", recordId, errorMsg);
        }
    }

    @Override
    public void onSkipInWrite(Employee item, Throwable t) {
        String name = (item != null) ? item.getEmployeeName() : "UNKNOWN";
        String errorMsg = t.getMessage();

        log.error("Skipped during WRITE [Name: {}]: Database Constraint Failure -> {}", name, errorMsg);
        auditLogger.info("PHASE: WRITE_DATABASE | RECORD ID: {} | ERROR: {}", name, errorMsg);

        publishToKafka("WRITE_DATABASE", name, errorMsg);
    }

    /**
     * Helper method to publish structural skip events asynchronously to Kafka.
     */
    private void publishToKafka(String phase, String recordId, String errorMessage) {
        SkipEvent event = new SkipEvent(phase, recordId, errorMessage, Instant.now());

        // Use recordId as the Kafka partition key to maintain order for specific records if retried
        kafkaTemplate.send(KAFKA_TOPIC, recordId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        // Fail-silent for Kafka to prevent halting the entire batch job due to a broker issue
                        log.error("Failed to send skip event to Kafka for Record ID: {}", recordId, ex);
                    } else {
                        log.debug("Successfully sent skip event to Kafka for Record ID: {} on topic {}", recordId, KAFKA_TOPIC);
                    }
                });
    }
}