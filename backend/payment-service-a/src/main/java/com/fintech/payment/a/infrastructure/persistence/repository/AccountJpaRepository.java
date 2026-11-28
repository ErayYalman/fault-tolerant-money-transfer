package com.fintech.payment.a.infrastructure.persistence.repository;

import com.fintech.payment.a.infrastructure.persistence.entity.AccountJpaEntity;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AccountJpaRepository
        extends JpaRepository<AccountJpaEntity, UUID> {

    Page<AccountJpaEntity> findByOwnerSubjectOrderByAccountNumberAscIdAsc(
            String ownerSubject,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT account
            FROM AccountJpaEntity account
            WHERE account.id IN :accountIds
            ORDER BY account.id ASC
            """)
    List<AccountJpaEntity> findAllByIdsForUpdate(
            @Param("accountIds") Collection<UUID> accountIds
    );
}
