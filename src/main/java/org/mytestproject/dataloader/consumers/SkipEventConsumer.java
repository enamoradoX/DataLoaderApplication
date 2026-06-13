package org.mytestproject.dataloader.consumers;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.configurations.KafkaConfiguration;
import org.mytestproject.dataloader.models.SkipEvent;
import org.mytestproject.dataloader.services.EmailNotificationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SkipEventConsumer {

    private final EmailNotificationService emailNotificationService;

    public SkipEventConsumer(EmailNotificationService emailNotificationService) {
        this.emailNotificationService = emailNotificationService;
    }

    /**
     * Listens for skip events produced by EmployeeSkipListener and turns each one into an alert email.
     * Uses the dedicated container factory wired in KafkaConfiguration; the group id comes from
     * spring.kafka.consumer.group-id in application.properties.
     */
    @KafkaListener(
            topics = KafkaConfiguration.KAFKA_TOPIC,
            containerFactory = "skipEventKafkaListenerContainerFactory")
    public void onSkipEvent(SkipEvent event) {
        log.info("Received skip event from Kafka -> phase={}, recordId={}", event.phase(), event.recordId());
        emailNotificationService.sendSkipAlert(event);
    }
}
