package com.fintech.transfer.infrastructure.persistence.repository;

import com.fintech.transfer.infrastructure.persistence.entity.SagaStateJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SagaStateJpaRepository
        extends JpaRepository<SagaStateJpaEntity, UUID> {

    Optional<SagaStateJpaEntity> findByTransferId(
            UUID transferId);
}