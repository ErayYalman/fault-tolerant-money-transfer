package com.fintech.payment.a.infrastructure.persistence.repository;

import com.fintech.payment.a.infrastructure.persistence.entity.OutboxJpaEntity;
import com.fintech.payment.a.infrastructure.persistence.entity.OutboxStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboxJpaRepository
        extends JpaRepository<OutboxJpaEntity, UUID> {

    List<OutboxJpaEntity> findByStatusOrderByCreatedAtAscIdAsc(
            OutboxStatus status,
            Pageable pageable
    );
}