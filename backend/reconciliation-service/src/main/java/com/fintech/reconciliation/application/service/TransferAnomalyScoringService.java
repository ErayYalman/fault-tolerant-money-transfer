package com.fintech.reconciliation.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fintech.reconciliation.domain.model.AnomalyFeatures;
import com.fintech.reconciliation.domain.model.AnomalyPrediction;
import com.fintech.reconciliation.domain.model.AnomalyScoreRecord;
import com.fintech.reconciliation.domain.model.LedgerEntryRecord;
import com.fintech.reconciliation.domain.model.TransferRecord;
import com.fintech.reconciliation.domain.port.out.AnomalyScoringProvider;

@Service
public class TransferAnomalyScoringService {

    private static final String DEBIT = "DEBIT";
    private static final String COMPLETED = "COMPLETED";

    private final AnomalyScoringProvider scoringProvider;

    public TransferAnomalyScoringService(
            AnomalyScoringProvider scoringProvider) {
        this.scoringProvider = scoringProvider;
    }

    public List<AnomalyScoreRecord> scoreEligibleTransfers(
            List<TransferRecord> transfers,
            List<LedgerEntryRecord> bankALedger,
            Instant scoredAt) {

        Map<UUID, List<LedgerEntryRecord>> debitEntriesByCorrelation = bankALedger.stream()
                .filter(entry -> DEBIT.equals(entry.entryType()))
                .filter(entry -> COMPLETED.equals(entry.status()))
                .collect(Collectors.groupingBy(
                        LedgerEntryRecord::correlationId));

        List<LedgerEntryRecord> completedDebits = bankALedger.stream()
                .filter(entry -> DEBIT.equals(entry.entryType()))
                .filter(entry -> COMPLETED.equals(entry.status()))
                .sorted(Comparator.comparing(
                        LedgerEntryRecord::createdAt))
                .toList();

        return transfers.stream()
                .map(transfer -> scoreIfEligible(
                        transfer,
                        debitEntriesByCorrelation.getOrDefault(
                                transfer.correlationId(),
                                List.of()),
                        completedDebits,
                        scoredAt))
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    private java.util.Optional<AnomalyScoreRecord> scoreIfEligible(
            TransferRecord transfer,
            List<LedgerEntryRecord> matchingDebits,
            List<LedgerEntryRecord> allCompletedDebits,
            Instant scoredAt) {

        if (!hasCompleteTimings(transfer) || matchingDebits.isEmpty()) {
            return java.util.Optional.empty();
        }

        LedgerEntryRecord debit = matchingDebits.get(0);

        long transactionDurationMs = millisBetween(
                transfer.startedAt(),
                transfer.completedAt());

        long debitProcessingTime = millisBetween(
                transfer.debitRequestedAt(),
                transfer.debitCompletedAt());

        long creditProcessingTime = millisBetween(
                transfer.creditRequestedAt(),
                transfer.creditCompletedAt());

        if (transactionDurationMs <= 0
                || debitProcessingTime < 0
                || creditProcessingTime < 0) {
            return java.util.Optional.empty();
        }

        Instant debitAt = debit.createdAt();

        int transactionsLast10min = countDebits(
                allCompletedDebits,
                debit.accountId(),
                debitAt.minus(Duration.ofMinutes(10)),
                debitAt);

        int transactionsLast1h = countDebits(
                allCompletedDebits,
                debit.accountId(),
                debitAt.minus(Duration.ofHours(1)),
                debitAt);

        // The current debit must be present in both windows.
        if (transactionsLast10min < 1
                || transactionsLast1h < transactionsLast10min) {
            return java.util.Optional.empty();
        }

        AnomalyFeatures features = new AnomalyFeatures(
                transfer.amount(),
                transactionDurationMs,
                debitProcessingTime,
                creditProcessingTime,
                transactionsLast10min,
                transactionsLast1h);

        AnomalyPrediction prediction = scoringProvider.score(features);

        return java.util.Optional.of(new AnomalyScoreRecord(
                transfer.transferId(),
                transfer.correlationId(),
                prediction.anomalyScore(),
                prediction.modelVersion(),
                prediction.isAnomaly(),
                scoredAt));
    }

    private boolean hasCompleteTimings(TransferRecord transfer) {
        return transfer.startedAt() != null
                && transfer.completedAt() != null
                && transfer.debitRequestedAt() != null
                && transfer.debitCompletedAt() != null
                && transfer.creditRequestedAt() != null
                && transfer.creditCompletedAt() != null;
    }

    private long millisBetween(Instant start, Instant end) {
        return Duration.between(start, end).toMillis();
    }

    private int countDebits(
            List<LedgerEntryRecord> entries,
            UUID accountId,
            Instant fromInclusive,
            Instant toInclusive) {

        return Math.toIntExact(entries.stream()
                .filter(entry -> accountId.equals(entry.accountId()))
                .filter(entry -> !entry.createdAt().isBefore(fromInclusive)
                        && !entry.createdAt().isAfter(toInclusive))
                .count());
    }
}