package com.fintech.reconciliation.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fintech.reconciliation.infrastructure.persistence.entity.ReconciliationDiscrepancyEntity;

public interface ReconciliationDiscrepancyRepository
        extends JpaRepository<ReconciliationDiscrepancyEntity, UUID> {

    List<ReconciliationDiscrepancyEntity> findByRunIdOrderByDetectedAtAsc(
            UUID runId);
}