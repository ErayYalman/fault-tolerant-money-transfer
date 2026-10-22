package com.fintech.reconciliation.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fintech.reconciliation.domain.model.AnomalyFeatures;
import com.fintech.reconciliation.domain.model.AnomalyPrediction;
import com.fintech.reconciliation.domain.model.AnomalyScoringResult;
import com.fintech.reconciliation.domain.model.AnomalyScoringStatus;
import com.fintech.reconciliation.domain.model.LedgerEntryRecord;
import com.fintech.reconciliation.domain.model.TransferRecord;
import com.fintech.reconciliation.domain.port.out.AnomalyScoringProvider;

@ExtendWith(MockitoExtension.class)
class TransferAnomalyScoringServiceTest {

        private static final UUID TRANSFER_ID = UUID.randomUUID();
        private static final UUID CORRELATION_ID = UUID.randomUUID();
        private static final UUID ACCOUNT_ID = UUID.randomUUID();

        private static final Instant STARTED_AT = Instant.parse("2026-10-20T10:00:00Z");

        private static final Instant DEBIT_AT = STARTED_AT.plusMillis(100);

        private static final Instant COMPLETED_AT = STARTED_AT.plusMillis(500);

        @Mock
        private AnomalyScoringProvider scoringProvider;

        private TransferAnomalyScoringService service;

        @BeforeEach
        void setUp() {
                service = new TransferAnomalyScoringService(scoringProvider);
        }

        @Test
        void shouldBuildTimingAndAccountFrequencyFeatures() {
                TransferRecord transfer = new TransferRecord(
                                TRANSFER_ID,
                                CORRELATION_ID,
                                new BigDecimal("250.00"),
                                "TRY",
                                "COMPLETED",
                                STARTED_AT,
                                STARTED_AT.plusMillis(20),
                                DEBIT_AT,
                                STARTED_AT.plusMillis(110),
                                COMPLETED_AT,
                                COMPLETED_AT);

                List<LedgerEntryRecord> debitEntries = List.of(
                                debit(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                DEBIT_AT.minusSeconds(1200)),
                                debit(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                DEBIT_AT.minusSeconds(300)),
                                debit(
                                                TRANSFER_ID,
                                                CORRELATION_ID,
                                                DEBIT_AT));

                when(scoringProvider.score(any(AnomalyFeatures.class)))
                                .thenReturn(new AnomalyPrediction(
                                                0.91,
                                                "iforest-test",
                                                true));

                Instant scoredAt = COMPLETED_AT.plusSeconds(1);

                AnomalyScoringResult result = service.scoreEligibleTransfers(
                                List.of(transfer),
                                debitEntries,
                                scoredAt);

                assertThat(result.scores()).hasSize(1);
                assertThat(result.scores().get(0).transferId()).isEqualTo(TRANSFER_ID);
                assertThat(result.scores().get(0).correlationId()).isEqualTo(CORRELATION_ID);
                assertThat(result.scores().get(0).anomalyScore()).isEqualTo(0.91);
                assertThat(result.scores().get(0).isAnomaly()).isTrue();

                assertThat(result.status())
                                .isEqualTo(AnomalyScoringStatus.COMPLETED);
                assertThat(result.attemptedCount()).isEqualTo(1);
                assertThat(result.failedCount()).isZero();

                ArgumentCaptor<AnomalyFeatures> captor = ArgumentCaptor.forClass(AnomalyFeatures.class);

                verify(scoringProvider).score(captor.capture());

                AnomalyFeatures features = captor.getValue();

                assertThat(features.amount())
                                .isEqualByComparingTo("250.00");
                assertThat(features.transactionDurationMs()).isEqualTo(500);
                assertThat(features.debitProcessingTime()).isEqualTo(80);
                assertThat(features.creditProcessingTime()).isEqualTo(390);
                assertThat(features.transactionsLast10min()).isEqualTo(2);
                assertThat(features.transactionsLast1h()).isEqualTo(3);
        }

        @Test
        void shouldSkipTransferWhenRequiredTimingsAreMissing() {
                TransferRecord transfer = new TransferRecord(
                                TRANSFER_ID,
                                CORRELATION_ID,
                                new BigDecimal("250.00"),
                                "TRY",
                                "CREDIT_FAILED",
                                STARTED_AT,
                                STARTED_AT.plusMillis(20),
                                DEBIT_AT,
                                null,
                                null,
                                null);

                AnomalyScoringResult result = service.scoreEligibleTransfers(
                                List.of(transfer),
                                List.of(debit(
                                                TRANSFER_ID,
                                                CORRELATION_ID,
                                                DEBIT_AT)),
                                COMPLETED_AT);

                assertThat(result.scores()).isEmpty();
                assertThat(result.status())
                                .isEqualTo(AnomalyScoringStatus.NOT_REQUIRED);
                assertThat(result.attemptedCount()).isZero();
                assertThat(result.failedCount()).isZero();
        }

        private LedgerEntryRecord debit(
                        UUID transferId,
                        UUID correlationId,
                        Instant createdAt) {

                return new LedgerEntryRecord(
                                transferId,
                                correlationId,
                                ACCOUNT_ID,
                                new BigDecimal("250.00"),
                                new BigDecimal("1000.00"),
                                new BigDecimal("750.00"),
                                "DEBIT",
                                "COMPLETED",
                                createdAt);
        }

        @Test
        void shouldPreserveSuccessfulScoresWhenOneScoringCallFails() {
                UUID secondTransferId = UUID.randomUUID();
                UUID secondCorrelationId = UUID.randomUUID();

                TransferRecord firstTransfer = new TransferRecord(
                                TRANSFER_ID,
                                CORRELATION_ID,
                                new BigDecimal("250.00"),
                                "TRY",
                                "COMPLETED",
                                STARTED_AT,
                                STARTED_AT.plusMillis(20),
                                DEBIT_AT,
                                STARTED_AT.plusMillis(110),
                                COMPLETED_AT,
                                COMPLETED_AT);

                TransferRecord secondTransfer = new TransferRecord(
                                secondTransferId,
                                secondCorrelationId,
                                new BigDecimal("300.00"),
                                "TRY",
                                "COMPLETED",
                                STARTED_AT.plusSeconds(1),
                                STARTED_AT.plusMillis(1020),
                                STARTED_AT.plusMillis(1100),
                                STARTED_AT.plusMillis(1110),
                                STARTED_AT.plusMillis(1500),
                                STARTED_AT.plusMillis(1500));

                List<LedgerEntryRecord> debitEntries = List.of(
                                debit(
                                                TRANSFER_ID,
                                                CORRELATION_ID,
                                                DEBIT_AT),
                                debit(
                                                secondTransferId,
                                                secondCorrelationId,
                                                STARTED_AT.plusMillis(1100)));

                when(scoringProvider.score(any(AnomalyFeatures.class)))
                                .thenReturn(new AnomalyPrediction(
                                                0.91,
                                                "iforest-test",
                                                true))
                                .thenThrow(new IllegalStateException("ML temporarily unavailable"));

                AnomalyScoringResult result = service.scoreEligibleTransfers(
                                List.of(firstTransfer, secondTransfer),
                                debitEntries,
                                COMPLETED_AT);

                assertThat(result.scores()).hasSize(1);
                assertThat(result.scores().get(0).transferId())
                                .isEqualTo(TRANSFER_ID);

                assertThat(result.status())
                                .isEqualTo(AnomalyScoringStatus.PARTIAL);

                assertThat(result.attemptedCount()).isEqualTo(2);
                assertThat(result.failedCount()).isEqualTo(1);
                assertThat(result.errorMessage())
                                .contains("ML scoring failed for 1 of 2 eligible transfer(s)");

                verify(scoringProvider, org.mockito.Mockito.times(2))
                                .score(any(AnomalyFeatures.class));
        }
}