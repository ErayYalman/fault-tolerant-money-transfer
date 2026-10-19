package com.fintech.payment.a.infrastructure.persistence.mapper;

import com.fintech.payment.a.domain.model.Account;
import com.fintech.payment.a.infrastructure.persistence.entity.AccountJpaEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AccountPersistenceMapper {

    default Account toDomain(AccountJpaEntity entity) {
        return Account.rehydrate(
                entity.getId(),
                entity.getAccountNumber(),
                entity.getBalance(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getUpdatedAt()
        );
    }
}