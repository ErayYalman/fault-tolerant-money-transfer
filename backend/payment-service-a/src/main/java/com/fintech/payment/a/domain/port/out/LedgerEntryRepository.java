package com.fintech.payment.a.domain.port.out;

import com.fintech.payment.a.domain.model.LedgerEntry;
import com.fintech.payment.a.domain.model.LedgerEntryType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository {

    void save(LedgerEntry entry);

    Optional<LedgerEntry> findByCorrelationIdAndEntryType(
            UUID correlationId,
            LedgerEntryType entryType
    );

    List<LedgerEntry> findByCreatedAtRange(
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size
    );
}