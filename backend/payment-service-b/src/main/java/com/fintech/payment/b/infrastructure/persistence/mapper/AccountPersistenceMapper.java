package com.fintech.payment.b.infrastructure.persistence.mapper;

import com.fintech.payment.b.domain.model.Account;
import com.fintech.payment.b.infrastructure.persistence.entity.AccountJpaEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AccountPersistenceMapper {

    public Account toDomain(AccountJpaEntity entity) {
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