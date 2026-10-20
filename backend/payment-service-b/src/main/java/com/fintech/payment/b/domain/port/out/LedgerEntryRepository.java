package com.fintech.payment.b.domain.port.out;

import com.fintech.payment.b.domain.model.LedgerEntry;

public interface LedgerEntryRepository {

    void save(LedgerEntry entry);
}