package com.fintech.transfer.infrastructure.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fintech.transfer.infrastructure.persistence.entity.OutboxJpaEntity;
import com.fintech.transfer.infrastructure.persistence.entity.OutboxStatus;

public interface OutboxJpaRepository extends JpaRepository<OutboxJpaEntity, UUID> {

    List<OutboxJpaEntity> findByStatusOrderByCreatedAtAscIdAsc(
            OutboxStatus status,
            Pageable pageable);
}