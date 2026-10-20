package com.fintech.payment.b.infrastructure.persistence.repository;

import java.util.UUID;

import com.fintech.payment.b.infrastructure.persistence.entity.LedgerEntryJpaEntity;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LedgerEntryPanacheRepository implements PanacheRepositoryBase<LedgerEntryJpaEntity, UUID> {
}