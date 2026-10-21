package com.fintech.reconciliation.domain.port.out;

import java.time.Instant;
import java.util.List;

import com.fintech.reconciliation.domain.model.TransferRecord;

public interface TransferDataProvider {

    List<TransferRecord> findTransfers(
            Instant fromInclusive,
            Instant toExclusive);
}