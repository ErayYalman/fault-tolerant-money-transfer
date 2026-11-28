package com.fintech.payment.a.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "internal_transfers",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_internal_transfers_owner_idempotency",
                        columnNames = {
                                "owner_subject",
                                "idempotency_key"
                        }
                ),
                @UniqueConstraint(
                        name = "internal_transfers_correlation_id_key",
                        columnNames = "correlation_id"
                )
        }
)
public class InternalTransferJpaEntity {

    @Id
    private UUID id;

    @Column(name = "correlation_id", nullable = false)
    private UUID correlationId;

    @Column(name = "owner_subject", nullable = false, length = 255)
    private String ownerSubject;

    @Column(name = "from_account_id", nullable = false)
    private UUID fromAccountId;

    @Column(name = "to_account_id", nullable = false)
    private UUID toAccountId;

    @Column(
            name = "amount",
            nullable = false,
            precision = 19,
            scale = 4
    )
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(
            name = "request_fingerprint",
            nullable = false,
            length = 64
    )
    private String requestFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InternalTransferJpaEntity() {
    }

    public InternalTransferJpaEntity(
            UUID id,
            UUID correlationId,
            String ownerSubject,
            UUID fromAccountId,
            UUID toAccountId,
            BigDecimal amount,
            String currency,
            String idempotencyKey,
            String requestFingerprint,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.correlationId = Objects.requireNonNull(correlationId, "correlationId must not be null");
        this.ownerSubject = Objects.requireNonNull(ownerSubject, "ownerSubject must not be null");
        this.fromAccountId = Objects.requireNonNull(fromAccountId, "fromAccountId must not be null");
        this.toAccountId = Objects.requireNonNull(toAccountId, "toAccountId must not be null");
        this.amount = Objects.requireNonNull(amount, "amount must not be null");
        this.currency = Objects.requireNonNull(currency, "currency must not be null");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        this.requestFingerprint = Objects.requireNonNull(requestFingerprint, "requestFingerprint must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public UUID getId() {
        return id;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public UUID getFromAccountId() {
        return fromAccountId;
    }

    public UUID getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestFingerprint() {
        return requestFingerprint;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}