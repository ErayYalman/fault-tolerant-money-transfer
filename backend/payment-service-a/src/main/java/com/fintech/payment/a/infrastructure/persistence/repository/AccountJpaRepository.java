package com.fintech.payment.a.infrastructure.persistence.repository;

import com.fintech.payment.a.infrastructure.persistence.entity.AccountJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AccountJpaRepository
        extends JpaRepository<AccountJpaEntity, UUID> {
}