package com.fintech.payment.a.infrastructure.messaging.outbox;

import com.fintech.payment.a.application.outbox.OutboxRelay;
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
            throw new IllegalArgumentException("batchSize must be positive");
        }

        this.outboxRelay = outboxRelay;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:1000}")
    public void publishPendingMessages() {
        try {
            int published = outboxRelay.publishPendingBatch(batchSize);

            if (published > 0) {
                log.info("Published {} payment Outbox event(s)", published);
            }
        } catch (RuntimeException exception) {
            log.error(
                    "Payment Outbox polling failed; pending events will be retried",
                    exception
            );
        }
    }
}