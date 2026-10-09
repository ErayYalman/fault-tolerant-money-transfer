package com.fintech.transfer.application.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fintech.transfer.application.messaging.PendingOutboxMessage;
import com.fintech.transfer.application.port.out.OutboxMessageTransport;
import com.fintech.transfer.application.port.out.OutboxRelayStore;

public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

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
        List<PendingOutboxMessage> messages = outboxStore.findPendingBatch(batchSize);

        int publishedCount = 0;

        for (PendingOutboxMessage message : messages) {
            try {
                transport.publish(message);

                Instant publishedAt = Instant.now(clock);
                outboxStore.markPublished(message.id(), publishedAt);

                publishedCount++;
            } catch (RuntimeException exception) {
                log.warn(
                        "Outbox publication failed for message {} of type {}. "
                                + "The message remains pending and later messages "
                                + "in this batch will not be processed.",
                        message.id(),
                        message.eventType(),
                        exception);

                // durdurur, çünkü bir sonraki mesajın yayınlanması, 
                // önceki mesajın hala beklemede olması durumunda gerçekleşemez.
                break;
            }
        }

        return publishedCount;
    }
}