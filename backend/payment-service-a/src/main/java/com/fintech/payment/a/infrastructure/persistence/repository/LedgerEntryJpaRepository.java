package com.fintech.payment.a.infrastructure.persistence.repository;

import com.fintech.payment.a.infrastructure.persistence.entity.LedgerEntryJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LedgerEntryJpaRepository
        extends JpaRepository<LedgerEntryJpaEntity, UUID> {
}