package com.fintech.payment.a.infrastructure.persistence.repository;

import com.fintech.payment.a.infrastructure.persistence.entity.InternalTransferJpaEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InternalTransferJpaRepository
        extends JpaRepository<InternalTransferJpaEntity, UUID> {

    Optional<InternalTransferJpaEntity>
    findByOwnerSubjectAndIdempotencyKey(
            String ownerSubject,
            String idempotencyKey
    );
}
