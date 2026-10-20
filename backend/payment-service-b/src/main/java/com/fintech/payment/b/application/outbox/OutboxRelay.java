package com.fintech.payment.b.application.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.jboss.logging.Logger;

import com.fintech.payment.b.application.messaging.PendingOutboxMessage;
import com.fintech.payment.b.application.port.out.OutboxMessageTransport;
import com.fintech.payment.b.application.port.out.OutboxRelayStore;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OutboxRelay {

    private static final Logger LOG = Logger.getLogger(OutboxRelay.class);

    private final OutboxRelayStore outboxStore;
    private final OutboxMessageTransport transport;
    private final Clock clock;

    public OutboxRelay(
            OutboxRelayStore outboxStore,
            OutboxMessageTransport transport,
            Clock clock) {
        this.outboxStore = Objects.requireNonNull(outboxStore);
        this.transport = Objects.requireNonNull(transport);
        this.clock = Objects.requireNonNull(clock);
    }

    public int publishPendingBatch(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be positive");
        }

        List<PendingOutboxMessage> messages = outboxStore.findPendingBatch(batchSize);

        int publishedCount = 0;

        for (PendingOutboxMessage message : messages) {
            try {
                transport.publish(message);
            } catch (RuntimeException exception) {
                LOG.warn(
                        "Outbox message remains pending after transport failure: "
                                + message.id(),
                        exception);
                break;
            }

            try {
                Instant publishedAt = Instant.now(clock);

                outboxStore.markPublished(
                        message.id(),
                        publishedAt);

                publishedCount++;
            } catch (RuntimeException exception) {
                LOG.error(
                        "Message was sent but Outbox status could not be updated; "
                                + "duplicate delivery may occur: " + message.id(),
                        exception);
                break;
            }
        }

        return publishedCount;
    }
}