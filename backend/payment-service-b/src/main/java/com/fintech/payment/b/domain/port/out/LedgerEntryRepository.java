package com.fintech.payment.b.domain.port.out;

import java.time.Instant;
import java.util.List;

import com.fintech.payment.b.domain.model.LedgerEntry;

public interface LedgerEntryRepository {

    void save(LedgerEntry entry);

    List<LedgerEntry> findByCreatedAtRange(
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size);
}