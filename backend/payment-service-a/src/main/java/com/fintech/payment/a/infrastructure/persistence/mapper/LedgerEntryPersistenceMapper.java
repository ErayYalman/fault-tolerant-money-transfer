package com.fintech.payment.a.infrastructure.persistence.mapper;

import com.fintech.payment.a.domain.model.LedgerEntry;
import com.fintech.payment.a.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LedgerEntryPersistenceMapper {

    LedgerEntryJpaEntity toEntity(LedgerEntry entry);
}