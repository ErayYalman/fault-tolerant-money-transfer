package com.fintech.transfer.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fintech.transfer.application.messaging.PendingOutboxMessage;

public interface OutboxRelayStore {

    List<PendingOutboxMessage> findPendingBatch(int batchSize);

    void markPublished(UUID messageId, Instant publishedAt);
}