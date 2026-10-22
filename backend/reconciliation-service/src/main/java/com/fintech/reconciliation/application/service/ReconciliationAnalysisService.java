package com.fintech.reconciliation.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fintech.reconciliation.domain.model.AnomalyScoringResult;
import com.fintech.reconciliation.domain.model.LedgerEntryRecord;
import com.fintech.reconciliation.domain.model.ReconciliationAnalysisResult;
import com.fintech.reconciliation.domain.model.ReconciliationDiscrepancy;
import com.fintech.reconciliation.domain.model.TransferRecord;
import com.fintech.reconciliation.domain.port.out.PaymentALedgerDataProvider;
import com.fintech.reconciliation.domain.port.out.PaymentBLedgerDataProvider;
import com.fintech.reconciliation.domain.port.out.TransferDataProvider;

@Service
public class ReconciliationAnalysisService {

        private final TransferDataProvider transferDataProvider;
        private final PaymentALedgerDataProvider paymentALedgerDataProvider;
        private final PaymentBLedgerDataProvider paymentBLedgerDataProvider;
        private final ReconciliationRuleEngine ruleEngine;
        private final TransferAnomalyScoringService anomalyScoringService;

        public ReconciliationAnalysisService(
                        TransferDataProvider transferDataProvider,
                        PaymentALedgerDataProvider paymentALedgerDataProvider,
                        PaymentBLedgerDataProvider paymentBLedgerDataProvider,
                        ReconciliationRuleEngine ruleEngine,
                        TransferAnomalyScoringService anomalyScoringService) {

                this.transferDataProvider = transferDataProvider;
                this.paymentALedgerDataProvider = paymentALedgerDataProvider;
                this.paymentBLedgerDataProvider = paymentBLedgerDataProvider;
                this.ruleEngine = ruleEngine;
                this.anomalyScoringService = anomalyScoringService;
        }

        public ReconciliationAnalysisResult analyze(
                        Instant fromInclusive,
                        Instant toExclusive) {

                if (fromInclusive == null
                                || toExclusive == null
                                || !fromInclusive.isBefore(toExclusive)) {
                        throw new IllegalArgumentException(
                                        "from must be earlier than to");
                }

                List<TransferRecord> transfers = transferDataProvider.findTransfers(
                                fromInclusive,
                                toExclusive);

                List<LedgerEntryRecord> bankAEntries = paymentALedgerDataProvider.findLedgerEntries(
                                fromInclusive.minus(Duration.ofHours(1)),
                                toExclusive);

                List<LedgerEntryRecord> bankBEntries = paymentBLedgerDataProvider.findLedgerEntries(
                                fromInclusive,
                                toExclusive);

                List<ReconciliationDiscrepancy> discrepancies = ruleEngine.evaluate(
                                transfers,
                                bankAEntries,
                                bankBEntries);

                AnomalyScoringResult anomalyScoringResult = anomalyScoringService.scoreEligibleTransfers(
                                transfers,
                                bankAEntries,
                                Instant.now());

                return new ReconciliationAnalysisResult(
                                transfers.size(),
                                discrepancies,
                                anomalyScoringResult);
        }
}