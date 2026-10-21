package com.fintech.payment.b.infrastructure.persistence.mapper;

import com.fintech.payment.b.domain.model.LedgerEntry;
import com.fintech.payment.b.infrastructure.persistence.entity.LedgerEntryJpaEntity;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LedgerEntryPersistenceMapper {

    public LedgerEntryJpaEntity toEntity(LedgerEntry entry) {
        LedgerEntryJpaEntity entity = new LedgerEntryJpaEntity();

        entity.setId(entry.id());
        entity.setTransferId(entry.transferId());
        entity.setCorrelationId(entry.correlationId());
        entity.setAccountId(entry.accountId());
        entity.setAmount(entry.amount());
        entity.setBalanceBefore(entry.balanceBefore());
        entity.setBalanceAfter(entry.balanceAfter());
        entity.setEntryType(entry.entryType());
        entity.setStatus(entry.status());
        entity.setCreatedAt(entry.createdAt());

        return entity;
    }

    public LedgerEntry toDomain(LedgerEntryJpaEntity entity) {
        return new LedgerEntry(
                entity.getId(),
                entity.getTransferId(),
                entity.getCorrelationId(),
                entity.getAccountId(),
                entity.getAmount(),
                entity.getBalanceBefore(),
                entity.getBalanceAfter(),
                entity.getEntryType(),
                entity.getStatus(),
                entity.getCreatedAt());
    }
}