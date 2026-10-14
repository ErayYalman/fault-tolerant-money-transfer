package com.fintech.transfer.infrastructure.messaging.outbox;

import com.fintech.transfer.application.outbox.OutboxRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.outbox.relay.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class OutboxRelayScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelay outboxRelay;
    private final int batchSize;

    public OutboxRelayScheduler(
            OutboxRelay outboxRelay,
            @Value("${app.outbox.batch-size:100}") int batchSize
    ) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "Outbox batch size must be positive"
            );
        }

        this.outboxRelay = outboxRelay;
        this.batchSize = batchSize;
    }

    @Scheduled(
            fixedDelayString = "${app.outbox.poll-interval-ms:1000}"
    )
    public void publishPendingMessages() {
        try {
            int publishedCount =
                    outboxRelay.publishPendingBatch(batchSize);

            if (publishedCount > 0) {
                log.info(
                        "Published {} outbox message(s)",
                        publishedCount
                );
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Outbox polling failed. Pending messages will be retried.",
                    exception
            );
        }
    }
}