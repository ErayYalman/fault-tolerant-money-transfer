package com.fintech.payment.a.domain.port.out;

import com.fintech.payment.a.domain.model.LedgerEntry;

public interface LedgerEntryRepository {

    void save(LedgerEntry entry);
}