package com.fintech.reconciliation.infrastructure.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fintech.reconciliation.infrastructure.persistence.entity.ReconciliationRunEntity;

public interface ReconciliationRunRepository
        extends JpaRepository<ReconciliationRunEntity, UUID> {
}