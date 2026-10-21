package com.fintech.reconciliation.infrastructure.adapter;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.fintech.reconciliation.domain.model.TransferRecord;
import com.fintech.reconciliation.domain.port.out.TransferDataProvider;
import com.fintech.reconciliation.infrastructure.client.TransferServiceClient;
import com.fintech.reconciliation.infrastructure.client.dto.TransferSnapshot;

@Component
public class TransferDataAdapter implements TransferDataProvider {

    private final TransferServiceClient client;

    public TransferDataAdapter(TransferServiceClient client) {
        this.client = client;
    }

    @Override
    public List<TransferRecord> findTransfers(
            Instant fromInclusive,
            Instant toExclusive) {

        return client.findTransfers(fromInclusive, toExclusive)
                .stream()
                .map(TransferDataAdapter::toDomain)
                .toList();
    }

    private static TransferRecord toDomain(TransferSnapshot snapshot) {
        return new TransferRecord(
                snapshot.transferId(),
                snapshot.correlationId(),
                snapshot.amount(),
                snapshot.currency(),
                snapshot.status(),
                snapshot.startedAt(),
                snapshot.debitRequestedAt(),
                snapshot.debitCompletedAt(),
                snapshot.creditRequestedAt(),
                snapshot.creditCompletedAt(),
                snapshot.completedAt());
    }
}