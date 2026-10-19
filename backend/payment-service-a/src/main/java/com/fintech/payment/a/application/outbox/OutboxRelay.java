package com.fintech.payment.a.application.outbox;

import com.fintech.payment.a.application.messaging.PendingOutboxMessage;
import com.fintech.payment.a.application.port.out.OutboxMessageTransport;
import com.fintech.payment.a.application.port.out.OutboxRelayStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class OutboxRelay {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRelayStore outboxStore;
    private final OutboxMessageTransport transport;
    private final Clock clock;

    public OutboxRelay(
            OutboxRelayStore outboxStore,
            OutboxMessageTransport transport,
            Clock clock
    ) {
        this.outboxStore = Objects.requireNonNull(outboxStore);
        this.transport = Objects.requireNonNull(transport);
        this.clock = Objects.requireNonNull(clock);
    }

    public int publishPendingBatch(int batchSize) {
        List<PendingOutboxMessage> messages =
                outboxStore.findPendingBatch(batchSize);

        int publishedCount = 0;

        for (PendingOutboxMessage message : messages) {
            try {
                transport.publish(message);
            } catch (RuntimeException exception) {
                log.warn(
                        "Outbox message {} remains pending after transport failure",
                        message.id(),
                        exception
                );
                break;
            }

            try {
                outboxStore.markPublished(
                        message.id(),
                        Instant.now(clock)
                );
                publishedCount++;
            } catch (RuntimeException exception) {
                log.error(
                        "Message {} was sent, but its Outbox status could not " +
                                "be updated; duplicate delivery may occur",
                        message.id(),
                        exception
                );
                break;
            }
        }

        return publishedCount;
    }
}