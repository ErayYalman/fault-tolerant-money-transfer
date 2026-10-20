package com.fintech.payment.b.infrastructure.persistence.repository;

import java.util.UUID;

import com.fintech.payment.b.infrastructure.persistence.entity.AccountJpaEntity;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AccountPanacheRepository implements PanacheRepositoryBase<AccountJpaEntity, UUID> {
}