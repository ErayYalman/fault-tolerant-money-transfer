package com.fintech.payment.a.infrastructure.persistence.mapper;

import com.fintech.payment.a.domain.model.LedgerEntry;
import com.fintech.payment.a.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LedgerEntryPersistenceMapper {

    LedgerEntryJpaEntity toEntity(LedgerEntry entry);

    default LedgerEntry toDomain(LedgerEntryJpaEntity entity) {
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
                entity.getCreatedAt()
        );
    }
}