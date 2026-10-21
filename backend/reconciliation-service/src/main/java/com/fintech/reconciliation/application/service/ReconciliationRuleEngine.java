package com.fintech.reconciliation.application.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.fintech.reconciliation.domain.model.DiscrepancyCode;
import com.fintech.reconciliation.domain.model.LedgerEntryRecord;
import com.fintech.reconciliation.domain.model.ReconciliationDiscrepancy;
import com.fintech.reconciliation.domain.model.TransferRecord;

@Component
public class ReconciliationRuleEngine {

    private static final String COMPLETED = "COMPLETED";
    private static final String DEBIT = "DEBIT";
    private static final String REVERSAL = "REVERSAL";
    private static final String CREDIT = "CREDIT";

    public List<ReconciliationDiscrepancy> evaluate(
            List<TransferRecord> transfers,
            List<LedgerEntryRecord> bankALedger,
            List<LedgerEntryRecord> bankBLedger) {

        Objects.requireNonNull(transfers, "transfers must not be null");
        Objects.requireNonNull(bankALedger, "bankALedger must not be null");
        Objects.requireNonNull(bankBLedger, "bankBLedger must not be null");

        Map<UUID, List<LedgerEntryRecord>> bankAByCorrelation = groupByCorrelationId(bankALedger);

        Map<UUID, List<LedgerEntryRecord>> bankBByCorrelation = groupByCorrelationId(bankBLedger);

        List<ReconciliationDiscrepancy> discrepancies = new ArrayList<>();

        for (TransferRecord transfer : transfers) {
            UUID correlationId = transfer.correlationId();

            List<LedgerEntryRecord> bankAEntries = bankAByCorrelation.getOrDefault(correlationId, List.of());

            List<LedgerEntryRecord> bankBEntries = bankBByCorrelation.getOrDefault(correlationId, List.of());

            detectMissingCredit(
                    transfer, bankAEntries, bankBEntries, discrepancies);

            detectAmountMismatch(
                    transfer, bankAEntries, bankBEntries, discrepancies);

            detectStatusMismatch(
                    transfer, bankAEntries, bankBEntries, discrepancies);
        }

        return List.copyOf(discrepancies);
    }

    private Map<UUID, List<LedgerEntryRecord>> groupByCorrelationId(
            List<LedgerEntryRecord> entries) {

        return entries.stream().collect(
                Collectors.groupingBy(LedgerEntryRecord::correlationId));
    }

    private void detectMissingCredit(
            TransferRecord transfer,
            List<LedgerEntryRecord> bankAEntries,
            List<LedgerEntryRecord> bankBEntries,
            List<ReconciliationDiscrepancy> discrepancies) {

        boolean completedDebit = bankAEntries.stream()
                .anyMatch(entry -> DEBIT.equals(entry.entryType())
                        && COMPLETED.equals(entry.status()));

        boolean completedCredit = bankBEntries.stream()
                .anyMatch(entry -> CREDIT.equals(entry.entryType())
                        && COMPLETED.equals(entry.status()));

        boolean completedReversal = bankAEntries.stream()
                .anyMatch(entry -> REVERSAL.equals(entry.entryType())
                        && COMPLETED.equals(entry.status()));

        if (completedDebit && !completedCredit && !completedReversal) {
            discrepancies.add(new ReconciliationDiscrepancy(
                    transfer.transferId(),
                    transfer.correlationId(),
                    DiscrepancyCode.MISSING_CREDIT,
                    Map.of(
                            "reason",
                            "A completed debit exists without a completed credit "
                                    + "or completed reversal.",
                            "transferStatus",
                            transfer.status(),
                            "debitAmount",
                            bankAEntries.stream()
                                    .filter(entry -> DEBIT.equals(entry.entryType()))
                                    .map(entry -> entry.amount().toPlainString())
                                    .collect(Collectors.joining(", ")))));
        }
    }

    private void detectAmountMismatch(
            TransferRecord transfer,
            List<LedgerEntryRecord> bankAEntries,
            List<LedgerEntryRecord> bankBEntries,
            List<ReconciliationDiscrepancy> discrepancies) {

        BigDecimal expectedAmount = transfer.amount();

        List<String> bankAMismatches = bankAEntries.stream()
                .filter(entry -> DEBIT.equals(entry.entryType())
                        || REVERSAL.equals(entry.entryType()))
                .filter(entry -> entry.amount().compareTo(expectedAmount) != 0)
                .map(entry -> entry.entryType() + "=" + entry.amount().toPlainString())
                .sorted()
                .toList();

        List<String> bankBMismatches = bankBEntries.stream()
                .filter(entry -> CREDIT.equals(entry.entryType()))
                .filter(entry -> entry.amount().compareTo(expectedAmount) != 0)
                .map(entry -> entry.entryType() + "=" + entry.amount().toPlainString())
                .sorted()
                .toList();

        if (!bankAMismatches.isEmpty() || !bankBMismatches.isEmpty()) {
            Map<String, String> details = new LinkedHashMap<>();
            details.put("expectedTransferAmount", expectedAmount.toPlainString());

            if (!bankAMismatches.isEmpty()) {
                details.put("bankAAmountMismatches",
                        String.join(", ", bankAMismatches));
            }

            if (!bankBMismatches.isEmpty()) {
                details.put("bankBAmountMismatches",
                        String.join(", ", bankBMismatches));
            }

            discrepancies.add(new ReconciliationDiscrepancy(
                    transfer.transferId(),
                    transfer.correlationId(),
                    DiscrepancyCode.AMOUNT_MISMATCH,
                    details));
        }
    }

    private void detectStatusMismatch(
            TransferRecord transfer,
            List<LedgerEntryRecord> bankAEntries,
            List<LedgerEntryRecord> bankBEntries,
            List<ReconciliationDiscrepancy> discrepancies) {

        boolean hasDebit = hasType(bankAEntries, DEBIT);
        boolean hasReversal = hasType(bankAEntries, REVERSAL);
        boolean hasCredit = hasType(bankBEntries, CREDIT);

        boolean unexpectedBankAEntry = bankAEntries.stream()
                .anyMatch(entry -> !DEBIT.equals(entry.entryType())
                        && !REVERSAL.equals(entry.entryType()));

        boolean unexpectedBankBEntry = bankBEntries.stream()
                .anyMatch(entry -> !CREDIT.equals(entry.entryType()));

        boolean nonCompletedLedgerEntry = bankAEntries.stream().anyMatch(entry -> !COMPLETED.equals(entry.status()))
                || bankBEntries.stream().anyMatch(entry -> !COMPLETED.equals(entry.status()));

        boolean ledgerTransferIdMismatch = bankAEntries.stream()
                .anyMatch(entry -> !transfer.transferId().equals(entry.transferId()))
                || bankBEntries.stream().anyMatch(entry -> !transfer.transferId().equals(entry.transferId()));

        boolean duplicateEntryType = countType(bankAEntries, DEBIT) > 1
                || countType(bankAEntries, REVERSAL) > 1
                || countType(bankBEntries, CREDIT) > 1;

        boolean lifecycleMismatch = switch (transfer.status()) {
            case "PENDING", "DEBIT_REQUESTED" ->
                !bankAEntries.isEmpty() || !bankBEntries.isEmpty();

            case "DEBIT_FAILED" ->
                !bankAEntries.isEmpty() || !bankBEntries.isEmpty();

            case "DEBIT_COMPLETED", "CREDIT_REQUESTED", "CREDIT_FAILED" ->
                !hasDebit || hasReversal || hasCredit;

            case "COMPENSATING" ->
                !hasDebit || hasCredit;

            case "COMPENSATED" ->
                !hasDebit || !hasReversal || hasCredit;

            case "COMPLETED" ->
                !hasDebit || !hasCredit || hasReversal;

            default -> true;
        };

        if (unexpectedBankAEntry
                || unexpectedBankBEntry
                || nonCompletedLedgerEntry
                || ledgerTransferIdMismatch
                || duplicateEntryType
                || lifecycleMismatch) {

            discrepancies.add(new ReconciliationDiscrepancy(
                    transfer.transferId(),
                    transfer.correlationId(),
                    DiscrepancyCode.STATUS_MISMATCH,
                    Map.of(
                            "transferStatus",
                            transfer.status(),
                            "bankAEntries",
                            describeEntries(bankAEntries),
                            "bankBEntries",
                            describeEntries(bankBEntries),
                            "ledgerTransferIdMismatch",
                            Boolean.toString(ledgerTransferIdMismatch))));
        }
    }

    private boolean hasType(
            List<LedgerEntryRecord> entries,
            String entryType) {

        return entries.stream()
                .anyMatch(entry -> entryType.equals(entry.entryType()));
    }

    private long countType(
            List<LedgerEntryRecord> entries,
            String entryType) {

        return entries.stream()
                .filter(entry -> entryType.equals(entry.entryType()))
                .count();
    }

    private String describeEntries(List<LedgerEntryRecord> entries) {
        if (entries.isEmpty()) {
            return "<none>";
        }

        return entries.stream()
                .map(entry -> entry.entryType()
                        + ":" + entry.status()
                        + ":transferId=" + entry.transferId())
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(", "));
    }
}
