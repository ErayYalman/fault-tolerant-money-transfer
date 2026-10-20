package com.fintech.payment.b.infrastructure.messaging.outbox;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.fintech.payment.b.application.messaging.OutboundMessage;
import com.fintech.payment.b.application.port.out.PaymentEventPublisher;
import com.fintech.payment.b.infrastructure.persistence.entity.OutboxJpaEntity;
import com.fintech.payment.b.infrastructure.persistence.entity.OutboxStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class OutboxPersistenceAdapter implements PaymentEventPublisher {

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
}