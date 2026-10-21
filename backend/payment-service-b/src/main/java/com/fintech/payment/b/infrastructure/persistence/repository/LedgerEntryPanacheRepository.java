package com.fintech.payment.b.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fintech.payment.b.infrastructure.persistence.entity.LedgerEntryJpaEntity;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LedgerEntryPanacheRepository
        implements PanacheRepositoryBase<LedgerEntryJpaEntity, UUID> {

    public List<LedgerEntryJpaEntity> findByCreatedAtRange(
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size) {

        return find(
                "createdAt >= ?1 and createdAt < ?2 "
                        + "order by createdAt asc, id asc",
                fromInclusive,
                toExclusive)
                .page(Page.of(page, size))
                .list();
    }
}