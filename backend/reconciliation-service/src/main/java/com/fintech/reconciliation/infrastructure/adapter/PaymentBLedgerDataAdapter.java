package com.fintech.reconciliation.infrastructure.adapter;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fintech.reconciliation.domain.model.LedgerEntryRecord;
import com.fintech.reconciliation.domain.port.out.PaymentBLedgerDataProvider;
import com.fintech.reconciliation.infrastructure.client.PaymentServiceBClient;
import com.fintech.reconciliation.infrastructure.client.dto.LedgerEntrySnapshot;

@Component
public class PaymentBLedgerDataAdapter
        implements PaymentBLedgerDataProvider {

    private final PaymentServiceBClient client;

    public PaymentBLedgerDataAdapter(PaymentServiceBClient client) {
        this.client = client;
    }

    @Override
    public List<LedgerEntryRecord> findLedgerEntries(
            Instant fromInclusive,
            Instant toExclusive) {

        return client.findLedgerEntries(fromInclusive, toExclusive)
                .stream()
                .map(PaymentBLedgerDataAdapter::toDomain)
                .toList();
    }

    private static LedgerEntryRecord toDomain(
            LedgerEntrySnapshot snapshot) {

        return new LedgerEntryRecord(
                snapshot.transferId(),
                snapshot.correlationId(),
                snapshot.accountId(),
                snapshot.amount(),
                snapshot.balanceBefore(),
                snapshot.balanceAfter(),
                snapshot.entryType(),
                snapshot.status(),
                snapshot.createdAt());
    }
}