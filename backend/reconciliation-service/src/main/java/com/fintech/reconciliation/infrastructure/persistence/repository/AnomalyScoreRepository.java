package com.fintech.reconciliation.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fintech.reconciliation.infrastructure.persistence.entity.AnomalyScoreEntity;

public interface AnomalyScoreRepository
        extends JpaRepository<AnomalyScoreEntity, UUID> {

    List<AnomalyScoreEntity> findByRunIdOrderByScoredAtAsc(UUID runId);
}