package com.fintech.transfer.infrastructure.messaging.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.transfer.application.messaging.OutboundMessage;
import com.fintech.transfer.application.messaging.PendingOutboxMessage;
import com.fintech.transfer.application.port.out.DomainEventPublisher;
import com.fintech.transfer.application.port.out.OutboxRelayStore;
import com.fintech.transfer.infrastructure.persistence.entity.OutboxJpaEntity;
import com.fintech.transfer.infrastructure.persistence.entity.OutboxStatus;
import com.fintech.transfer.infrastructure.persistence.repository.OutboxJpaRepository;

@Repository
public class OutboxPersistenceAdapter
        implements DomainEventPublisher, OutboxRelayStore {

    private final OutboxJpaRepository repository;
    private final Clock clock;

    public OutboxPersistenceAdapter(
            OutboxJpaRepository repository,
            Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(OutboundMessage message) {
        OutboxJpaEntity entity = new OutboxJpaEntity();

        entity.setId(UUID.randomUUID());
        entity.setAggregateType(message.aggregateType());
        entity.setAggregateId(message.aggregateId());
        entity.setEventType(message.eventType());
        entity.setPayload(message.payload());
        entity.setStatus(OutboxStatus.PENDING);
        entity.setCreatedAt(Instant.now(clock));

        repository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingOutboxMessage> findPendingBatch(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be greater than zero");
        }

        return repository.findByStatusOrderByCreatedAtAscIdAsc(
                OutboxStatus.PENDING,
                PageRequest.of(0, batchSize))
                .stream()
                .map(entity -> new PendingOutboxMessage(
                        entity.getId(),
                        entity.getAggregateType(),
                        entity.getAggregateId(),
                        entity.getEventType(),
                        entity.getPayload(),
                        entity.getCreatedAt()))
                .toList();
    }

    @Override
    @Transactional
    public void markPublished(UUID messageId, Instant publishedAt) {
        OutboxJpaEntity entity = repository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Outbox message not found: " + messageId));

        if (entity.getStatus() == OutboxStatus.PUBLISHED) {
            return;
        }

        entity.setStatus(OutboxStatus.PUBLISHED);
        entity.setPublishedAt(publishedAt);

        repository.save(entity);
    }
}