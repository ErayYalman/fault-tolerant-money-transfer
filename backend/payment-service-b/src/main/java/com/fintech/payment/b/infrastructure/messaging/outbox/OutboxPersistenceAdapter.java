package com.fintech.payment.b.infrastructure.messaging.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.fintech.payment.b.application.messaging.OutboundMessage;
import com.fintech.payment.b.application.messaging.PendingOutboxMessage;
import com.fintech.payment.b.application.port.out.OutboxRelayStore;
import com.fintech.payment.b.application.port.out.PaymentEventPublisher;
import com.fintech.payment.b.infrastructure.persistence.entity.OutboxJpaEntity;
import com.fintech.payment.b.infrastructure.persistence.entity.OutboxStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OutboxPersistenceAdapter
        implements PaymentEventPublisher, OutboxRelayStore {

    private final EntityManager entityManager;
    private final Clock clock;

    public OutboxPersistenceAdapter(
            EntityManager entityManager,
            Clock clock) {
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Override
    @Transactional(Transactional.TxType.MANDATORY)
    public void append(OutboundMessage message) {
        Objects.requireNonNull(message, "message must not be null");

        OutboxJpaEntity entity = new OutboxJpaEntity();

        entity.setId(UUID.randomUUID());
        entity.setAggregateType(message.aggregateType());
        entity.setAggregateId(message.aggregateId());
        entity.setEventType(message.eventType());
        entity.setPayload(message.payload());
        entity.setStatus(OutboxStatus.PENDING);
        entity.setCreatedAt(Instant.now(clock));

        entityManager.persist(entity);
    }

    @Override
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<PendingOutboxMessage> findPendingBatch(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException(
                    "batchSize must be positive");
        }

        return entityManager.createQuery(
                """
                        SELECT e
                        FROM OutboxJpaEntity e
                        WHERE e.status = :status
                        ORDER BY e.createdAt ASC, e.id ASC
                        """,
                OutboxJpaEntity.class)
                .setParameter("status", OutboxStatus.PENDING)
                .setMaxResults(batchSize)
                .getResultList()
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
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void markPublished(UUID messageId, Instant publishedAt) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");

        OutboxJpaEntity entity = entityManager.find(
                OutboxJpaEntity.class,
                messageId,
                LockModeType.PESSIMISTIC_WRITE);

        if (entity == null) {
            throw new IllegalArgumentException(
                    "Outbox message not found: " + messageId);
        }

        if (entity.getStatus() == OutboxStatus.PUBLISHED) {
            return;
        }

        entity.setStatus(OutboxStatus.PUBLISHED);
        entity.setPublishedAt(publishedAt);
    }
}