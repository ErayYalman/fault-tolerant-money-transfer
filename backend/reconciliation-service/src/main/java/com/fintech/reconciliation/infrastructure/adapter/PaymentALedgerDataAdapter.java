package com.fintech.reconciliation.infrastructure.adapter;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fintech.reconciliation.domain.model.LedgerEntryRecord;
import com.fintech.reconciliation.domain.port.out.PaymentALedgerDataProvider;
import com.fintech.reconciliation.infrastructure.client.PaymentServiceAClient;
import com.fintech.reconciliation.infrastructure.client.dto.LedgerEntrySnapshot;

@Component
public class PaymentALedgerDataAdapter
        implements PaymentALedgerDataProvider {

    private final PaymentServiceAClient client;

    public PaymentALedgerDataAdapter(PaymentServiceAClient client) {
        this.client = client;
    }

    @Override
    public List<LedgerEntryRecord> findLedgerEntries(
            Instant fromInclusive,
            Instant toExclusive) {

        return client.findLedgerEntries(fromInclusive, toExclusive)
                .stream()
                .map(PaymentALedgerDataAdapter::toDomain)
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