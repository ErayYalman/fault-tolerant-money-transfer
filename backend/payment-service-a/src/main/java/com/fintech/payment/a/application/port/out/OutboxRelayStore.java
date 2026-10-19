package com.fintech.payment.a.application.port.out;

import com.fintech.payment.a.application.messaging.PendingOutboxMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRelayStore {

    List<PendingOutboxMessage> findPendingBatch(int batchSize);

    void markPublished(UUID messageId, Instant publishedAt);
}