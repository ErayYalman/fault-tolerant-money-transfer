package com.fintech.payment.a.infrastructure.persistence.mapper;

import com.fintech.payment.a.domain.model.InternalTransfer;
import com.fintech.payment.a.infrastructure.persistence.entity.InternalTransferJpaEntity;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InternalTransferPersistenceMapper {

    InternalTransferJpaEntity toEntity(InternalTransfer transfer);

    default InternalTransfer toDomain(InternalTransferJpaEntity entity) {
        return new InternalTransfer(
                entity.getId(),
                entity.getCorrelationId(),
                entity.getOwnerSubject(),
                entity.getFromAccountId(),
                entity.getToAccountId(),
                entity.getAmount(),
                entity.getCurrency(),
                entity.getIdempotencyKey(),
                entity.getRequestFingerprint(),
                entity.getCreatedAt()
        );
    }
}
