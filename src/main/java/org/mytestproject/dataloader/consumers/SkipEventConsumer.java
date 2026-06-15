package org.mytestproject.dataloader.consumers;

import lombok.extern.slf4j.Slf4j;
import org.mytestproject.dataloader.configurations.KafkaConfiguration;
import org.mytestproject.dataloader.models.SkipEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SkipEventConsumer {

    /**
     * Logs skip events from Kafka. Email is no longer sent per skip — it's sent once per load as
     * an end-of-run digest (see JobPerformanceListener / DataLoaderService). This consumer stays
     * as a working example hook for other services that may want to react to individual skips.
     */
    @KafkaListener(
            topics = KafkaConfiguration.KAFKA_TOPIC,
            containerFactory = "skipEventKafkaListenerContainerFactory")
    public void onSkipEvent(SkipEvent event) {
        log.info("Received skip event from Kafka -> phase={}, recordId={}", event.phase(), event.recordId());
    }
}
