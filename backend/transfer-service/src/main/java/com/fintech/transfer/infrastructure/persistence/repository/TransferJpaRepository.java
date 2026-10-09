package com.fintech.transfer.infrastructure.persistence.repository;

import com.fintech.transfer.infrastructure.persistence.entity.TransferJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, UUID> {

    Optional<TransferJpaEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<TransferJpaEntity> findByCorrelationId(UUID correlationId);
    
}