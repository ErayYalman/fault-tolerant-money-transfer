package com.fintech.transfer.unit.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fintech.transfer.application.saga.SagaAction;
import com.fintech.transfer.application.saga.SagaDecision;
import com.fintech.transfer.application.saga.TransferSagaOrchestrator;
import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.SagaState;
import com.fintech.transfer.domain.model.SagaStep;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.model.TransferStatus;
import com.fintech.transfer.domain.port.out.SagaStateRepository;
import com.fintech.transfer.domain.port.out.TransferRepository;

class TransferSagaOrchestratorTest {

    private static final Instant T1 = Instant.parse("2026-10-08T10:00:00Z");

    private static final Instant T2 = Instant.parse("2026-10-08T10:00:01Z");

    private static final Instant T3 = Instant.parse("2026-10-08T10:00:02Z");

    private static final Instant T4 = Instant.parse("2026-10-08T10:00:03Z");

    private static final Instant T5 = Instant.parse("2026-10-08T10:00:04Z");

    private static final Instant T6 = Instant.parse("2026-10-08T10:00:05Z");

    private TransferRepository transferRepository;
    private SagaStateRepository sagaStateRepository;

    private TransferSagaOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        transferRepository = mock(TransferRepository.class);
        sagaStateRepository = mock(SagaStateRepository.class);

        orchestrator = new TransferSagaOrchestrator(
                transferRepository,
                sagaStateRepository);
    }

    @Test
    void shouldRequestDebitFromPending() {
        // 1. Önce transferi yarat
        Transfer transfer = createTransfer();

        // 2. ID'yi kendi elimizle rastgele vermek yerine transferin kendisinden al
        UUID transferId = transfer.getId();

        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                transferId,
                T1);

        when(transferRepository.findById(transferId))
                .thenReturn(java.util.Optional.of(transfer));

        when(sagaStateRepository.findByTransferId(transferId))
                .thenReturn(java.util.Optional.of(sagaState));

        SagaDecision decision = orchestrator.startDebit(
                transferId,
                T2);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.DEBIT_REQUESTED);

        assertThat(transfer.getDebitRequestedAt())
                .isEqualTo(T2);

        assertThat(decision.action())
                .isEqualTo(SagaAction.REQUEST_DEBIT);

        verify(transferRepository)
                .save(transfer);

        verify(sagaStateRepository)
                .save(sagaState);
    }

    @Test
    void shouldRequestCreditAfterDebitCompleted() {
        Transfer transfer = createTransfer();

        transfer.requestDebit(T2);

        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                transfer.getId(),
                T1);

        when(transferRepository
                .findByCorrelationId(transfer.getCorrelationId()))
                .thenReturn(java.util.Optional.of(transfer));

        when(sagaStateRepository
                .findByTransferId(transfer.getId()))
                .thenReturn(java.util.Optional.of(sagaState));

        SagaDecision decision = orchestrator.onDebitCompleted(
                transfer.getCorrelationId(),
                T3);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.DEBIT_COMPLETED);

        assertThat(transfer.getDebitCompletedAt())
                .isEqualTo(T3);

        assertThat(sagaState.getCurrentStep())
                .isEqualTo(SagaStep.CREDIT);

        assertThat(decision.action())
                .isEqualTo(SagaAction.REQUEST_CREDIT);
    }

    @Test
    void shouldFinishSagaAfterDebitFailure() {
        Transfer transfer = createTransfer();

        transfer.requestDebit(T2);

        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                transfer.getId(),
                T1);

        when(transferRepository
                .findByCorrelationId(transfer.getCorrelationId()))
                .thenReturn(java.util.Optional.of(transfer));

        when(sagaStateRepository
                .findByTransferId(transfer.getId()))
                .thenReturn(java.util.Optional.of(sagaState));

        SagaDecision decision = orchestrator.onDebitFailed(
                transfer.getCorrelationId(),
                "INSUFFICIENT_FUNDS",
                T3);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.DEBIT_FAILED);

        assertThat(transfer.getFailureReason())
                .isEqualTo("INSUFFICIENT_FUNDS");

        assertThat(sagaState.getCurrentStep())
                .isEqualTo(SagaStep.FINISHED);

        assertThat(decision.action())
                .isEqualTo(SagaAction.NONE);
    }

    @Test
    void shouldRequestCompensationAfterCreditFailure() {
        Transfer transfer = createTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);
        transfer.requestCredit(T4);

        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                transfer.getId(),
                T1);

        sagaState.moveToCredit(T3);

        when(transferRepository
                .findByCorrelationId(transfer.getCorrelationId()))
                .thenReturn(java.util.Optional.of(transfer));

        when(sagaStateRepository
                .findByTransferId(transfer.getId()))
                .thenReturn(java.util.Optional.of(sagaState));

        SagaDecision decision = orchestrator.onCreditFailed(
                transfer.getCorrelationId(),
                "CREDIT_SERVICE_FAILURE",
                T5);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.CREDIT_FAILED);

        assertThat(transfer.getFailureReason())
                .isEqualTo("CREDIT_SERVICE_FAILURE");

        assertThat(sagaState.getCurrentStep())
                .isEqualTo(SagaStep.COMPENSATION);

        assertThat(decision.action())
                .isEqualTo(
                        SagaAction.REQUEST_DEBIT_COMPENSATION);
    }

    @Test
    void shouldCompleteSuccessfulSaga() {
        Transfer transfer = createTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);
        transfer.requestCredit(T4);

        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                transfer.getId(),
                T1);

        sagaState.moveToCredit(T3);

        when(transferRepository
                .findByCorrelationId(transfer.getCorrelationId()))
                .thenReturn(java.util.Optional.of(transfer));

        when(sagaStateRepository
                .findByTransferId(transfer.getId()))
                .thenReturn(java.util.Optional.of(sagaState));

        SagaDecision decision = orchestrator.onCreditCompleted(
                transfer.getCorrelationId(),
                T5);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.COMPLETED);

        assertThat(sagaState.getCurrentStep())
                .isEqualTo(SagaStep.FINISHED);

        assertThat(decision.action())
                .isEqualTo(SagaAction.NONE);
    }

    @Test
    void shouldCompleteCompensation() {
        Transfer transfer = createTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);
        transfer.requestCredit(T4);
        transfer.failCredit(
                "CREDIT_SERVICE_FAILURE",
                T5);
        transfer.startCompensation(T6);

        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                transfer.getId(),
                T1);

        sagaState.moveToCredit(T3);
        sagaState.moveToCompensation(T5);

        when(transferRepository
                .findByCorrelationId(transfer.getCorrelationId()))
                .thenReturn(java.util.Optional.of(transfer));

        when(sagaStateRepository
                .findByTransferId(transfer.getId()))
                .thenReturn(java.util.Optional.of(sagaState));

        SagaDecision decision = orchestrator.onDebitCompensated(
                transfer.getCorrelationId(),
                T6);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.COMPENSATED);

        assertThat(transfer.getCompletedAt())
                .isEqualTo(T6);

        assertThat(sagaState.getCurrentStep())
                .isEqualTo(SagaStep.FINISHED);

        assertThat(decision.action())
                .isEqualTo(SagaAction.NONE);
    }

    private Transfer createTransfer() {
        return Transfer.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "saga-" + UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Money(
                        new BigDecimal("1000.00"),
                        "TRY"),
                T1);
    }
}