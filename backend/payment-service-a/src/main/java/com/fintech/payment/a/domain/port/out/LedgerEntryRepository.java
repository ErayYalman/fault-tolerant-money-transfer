package com.fintech.payment.a.domain.port.out;

import com.fintech.payment.a.domain.model.LedgerEntry;
import com.fintech.payment.a.domain.model.LedgerEntryType;

import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository {

    void save(LedgerEntry entry);

    Optional<LedgerEntry> findByCorrelationIdAndEntryType(
            UUID correlationId,
            LedgerEntryType entryType
    );
}