package com.fintech.payment.b.infrastructure.messaging.outbox;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fintech.payment.b.application.outbox.OutboxRelay;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OutboxRelayScheduler {

    private static final Logger LOG = Logger.getLogger(OutboxRelayScheduler.class);

    private final OutboxRelay outboxRelay;
    private final int batchSize;

    public OutboxRelayScheduler(
            OutboxRelay outboxRelay,
            @ConfigProperty(name = "app.outbox.batch-size") int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be positive");
        }

        this.outboxRelay = outboxRelay;
        this.batchSize = batchSize;
    }

    @Scheduled(every = "{app.outbox.poll-interval}")
    void publishPendingMessages() {
        try {
            int published = outboxRelay.publishPendingBatch(batchSize);

            if (published > 0) {
                LOG.infof(
                        "Published %d Bank B Outbox event(s)",
                        published);
            }
        } catch (RuntimeException exception) {
            LOG.error(
                    "Bank B Outbox polling failed; pending events will be retried",
                    exception);
        }
    }
}