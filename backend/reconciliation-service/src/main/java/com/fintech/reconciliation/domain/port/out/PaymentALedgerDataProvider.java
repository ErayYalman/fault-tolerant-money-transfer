package com.fintech.reconciliation.domain.port.out;

import java.time.Instant;
import java.util.List;

import com.fintech.reconciliation.domain.model.LedgerEntryRecord;

public interface PaymentALedgerDataProvider {

    List<LedgerEntryRecord> findLedgerEntries(
            Instant fromInclusive,
            Instant toExclusive);
}