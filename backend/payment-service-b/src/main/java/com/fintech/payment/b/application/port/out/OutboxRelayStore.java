package com.fintech.payment.b.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fintech.payment.b.application.messaging.PendingOutboxMessage;

public interface OutboxRelayStore {

    List<PendingOutboxMessage> findPendingBatch(int batchSize);

    void markPublished(UUID messageId, Instant publishedAt);
}