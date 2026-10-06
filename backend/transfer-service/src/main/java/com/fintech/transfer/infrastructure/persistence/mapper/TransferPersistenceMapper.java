package com.fintech.transfer.infrastructure.persistence.mapper;

import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.infrastructure.persistence.entity.TransferJpaEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface TransferPersistenceMapper {

    @Mapping(target = "amount", source = "money.amount")
    @Mapping(target = "currency", source = "money.currency")
    TransferJpaEntity toEntity(Transfer transfer);

    default Transfer toDomain(TransferJpaEntity entity) {
        return Transfer.rehydrate(
                entity.getId(),
                entity.getCorrelationId(),
                entity.getIdempotencyKey(),
                entity.getFromAccountId(),
                entity.getToAccountId(),
                new Money(
                        entity.getAmount(),
                        entity.getCurrency()
                ),
                entity.getStatus(),
                entity.getFailureReason(),
                entity.getStartedAt(),
                entity.getDebitRequestedAt(),
                entity.getDebitCompletedAt(),
                entity.getCreditRequestedAt(),
                entity.getCreditCompletedAt(),
                entity.getCompletedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}