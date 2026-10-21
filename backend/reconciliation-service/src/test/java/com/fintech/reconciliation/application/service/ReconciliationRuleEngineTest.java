package com.fintech.reconciliation.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fintech.reconciliation.domain.model.DiscrepancyCode;
import com.fintech.reconciliation.domain.model.LedgerEntryRecord;
import com.fintech.reconciliation.domain.model.ReconciliationDiscrepancy;
import com.fintech.reconciliation.domain.model.TransferRecord;

class ReconciliationRuleEngineTest {

    private static final UUID TRANSFER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID CORRELATION_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static final UUID ACCOUNT_A_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");

    private static final UUID ACCOUNT_B_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");

    private static final Instant CREATED_AT = Instant.parse("2026-10-20T10:00:00Z");

    private ReconciliationRuleEngine ruleEngine;

    @BeforeEach
    void setUp() {
        ruleEngine = new ReconciliationRuleEngine();
    }

    @Test
    void shouldFindNoDiscrepanciesForCompletedTransferWithMatchingLedgers() {
        TransferRecord transfer = transfer("COMPLETED", "100.00");

        LedgerEntryRecord debit = bankAEntry("DEBIT", "100.00");

        LedgerEntryRecord credit = bankBEntry("CREDIT", "100.00");

        List<ReconciliationDiscrepancy> result = ruleEngine.evaluate(
                List.of(transfer),
                List.of(debit),
                List.of(credit));

        assertThat(result).isEmpty();
    }

    @Test
    void shouldDetectMissingCreditWhenDebitHasNoCreditOrReversal() {
        TransferRecord transfer = transfer("COMPLETED", "100.00");

        LedgerEntryRecord debit = bankAEntry("DEBIT", "100.00");

        List<ReconciliationDiscrepancy> result = ruleEngine.evaluate(
                List.of(transfer),
                List.of(debit),
                List.of());

        assertThat(result)
                .extracting(ReconciliationDiscrepancy::ruleCode)
                .containsExactlyInAnyOrder(
                        DiscrepancyCode.MISSING_CREDIT,
                        DiscrepancyCode.STATUS_MISMATCH);
    }

    @Test
    void shouldDetectAmountMismatchWithoutConfusingDecimalScale() {
        TransferRecord transfer = transfer("COMPLETED", "100.00");

        LedgerEntryRecord debit = bankAEntry("DEBIT", "100.0");

        LedgerEntryRecord credit = bankBEntry("CREDIT", "101.00");

        List<ReconciliationDiscrepancy> result = ruleEngine.evaluate(
                List.of(transfer),
                List.of(debit),
                List.of(credit));

        assertThat(result)
                .extracting(ReconciliationDiscrepancy::ruleCode)
                .containsExactly(DiscrepancyCode.AMOUNT_MISMATCH);
    }

    @Test
    void shouldNotFlagSuccessfullyCompensatedTransferAsMissingCredit() {
        TransferRecord transfer = transfer("COMPENSATED", "100.00");

        LedgerEntryRecord debit = bankAEntry("DEBIT", "100.00");

        LedgerEntryRecord reversal = bankAEntry("REVERSAL", "100.00");

        List<ReconciliationDiscrepancy> result = ruleEngine.evaluate(
                List.of(transfer),
                List.of(debit, reversal),
                List.of());

        assertThat(result).isEmpty();
    }

    @Test
    void shouldDetectLedgerEntryLinkedToDifferentTransferId() {
        TransferRecord transfer = transfer("COMPLETED", "100.00");

        LedgerEntryRecord inconsistentDebit = new LedgerEntryRecord(
                UUID.fromString("22222222-2222-2222-2222-222222222222"),
                CORRELATION_ID,
                ACCOUNT_A_ID,
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                new BigDecimal("100.00"),
                "DEBIT",
                "COMPLETED",
                CREATED_AT);

        LedgerEntryRecord credit = bankBEntry("CREDIT", "100.00");

        List<ReconciliationDiscrepancy> result = ruleEngine.evaluate(
                List.of(transfer),
                List.of(inconsistentDebit),
                List.of(credit));

        assertThat(result)
                .extracting(ReconciliationDiscrepancy::ruleCode)
                .containsExactly(DiscrepancyCode.STATUS_MISMATCH);
    }

    private TransferRecord transfer(String status, String amount) {
        return new TransferRecord(
                TRANSFER_ID,
                CORRELATION_ID,
                new BigDecimal(amount),
                "TRY",
                status,
                CREATED_AT,
                CREATED_AT,
                null,
                null,
                null,
                null);
    }

    private LedgerEntryRecord bankAEntry(
            String type,
            String amount) {

        BigDecimal value = new BigDecimal(amount);

        BigDecimal balanceBefore = "REVERSAL".equals(type)
                ? new BigDecimal("1000.00").subtract(value)
                : new BigDecimal("1000.00");

        BigDecimal balanceAfter = "REVERSAL".equals(type)
                ? new BigDecimal("1000.00")
                : balanceBefore.subtract(value);

        return new LedgerEntryRecord(
                TRANSFER_ID,
                CORRELATION_ID,
                ACCOUNT_A_ID,
                value,
                balanceBefore,
                balanceAfter,
                type,
                "COMPLETED",
                CREATED_AT);
    }

    private LedgerEntryRecord bankBEntry(
            String type,
            String amount) {

        BigDecimal value = new BigDecimal(amount);
        BigDecimal balanceBefore = new BigDecimal("500.00");
        BigDecimal balanceAfter = balanceBefore.add(value); // 500.00 + 100.00 = 600.00

        return new LedgerEntryRecord(
                TRANSFER_ID,
                CORRELATION_ID,
                ACCOUNT_B_ID,
                value,
                balanceBefore,
                balanceAfter,
                type,
                "COMPLETED",
                CREATED_AT);
    }
}