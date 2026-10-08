package com.fintech.transfer.unit.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fintech.transfer.domain.model.SagaState;
import com.fintech.transfer.domain.model.SagaStep;

class SagaStateTest {

    private static final Instant T1 = Instant.parse("2026-10-08T10:00:00Z");

    private static final Instant T2 = Instant.parse("2026-10-08T10:00:01Z");

    private static final Instant T3 = Instant.parse("2026-10-08T10:00:02Z");

    private static final Instant T4 = Instant.parse("2026-10-08T10:00:03Z");

    private static final Instant T5 = Instant.parse("2026-10-08T10:00:04Z");

    @Test
    void shouldStartWithDebitStep() {
        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                T1);

        assertThat(sagaState.getCurrentStep())
                .isEqualTo(SagaStep.DEBIT);

        assertThat(sagaState.getPayload())
                .isEmpty();

        assertThat(sagaState.getUpdatedAt())
                .isEqualTo(T1);
    }

    @Test
    void shouldMoveFromDebitToCredit() {
        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                T1);

        sagaState.moveToCredit(T2);

        assertThat(sagaState.getCurrentStep())
                .isEqualTo(SagaStep.CREDIT);

        assertThat(sagaState.getUpdatedAt())
                .isEqualTo(T2);
    }

    @Test
    void shouldMoveFromCreditToCompensation() {
        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                T1);

        sagaState.moveToCredit(T2);
        sagaState.moveToCompensation(T3);

        assertThat(sagaState.getCurrentStep())
                .isEqualTo(SagaStep.COMPENSATION);

        assertThat(sagaState.getUpdatedAt())
                .isEqualTo(T3);
    }

    @Test
    void shouldFinishSuccessfulSaga() {
        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                T1);

        sagaState.moveToCredit(T2);
        sagaState.finish(T3);

        assertThat(sagaState.getCurrentStep())
                .isEqualTo(SagaStep.FINISHED);
    }

    @Test
    void shouldFinishCompensatedSaga() {
        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                T1);

        sagaState.moveToCredit(T2);
        sagaState.moveToCompensation(T3);
        sagaState.finish(T4);

        assertThat(sagaState.getCurrentStep())
                .isEqualTo(SagaStep.FINISHED);
    }

    @Test
    void shouldFinishAfterDebitFailure() {
        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                T1);

        sagaState.finish(T2);

        assertThat(sagaState.getCurrentStep())
                .isEqualTo(SagaStep.FINISHED);
    }

    @Test
    void shouldRejectInvalidDebitToCompensationTransition() {
        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                T1);

        assertThatThrownBy(() -> sagaState.moveToCompensation(T2))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectTransitionAfterFinished() {
        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                T1);

        sagaState.finish(T2);

        assertThatThrownBy(() -> sagaState.moveToCredit(T3))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldStoreSagaContextPayload() {
        SagaState sagaState = SagaState.start(
                UUID.randomUUID(),
                UUID.randomUUID(),
                T1);

        sagaState.putPayload(
                "debitAttempt",
                1,
                T5);

        assertThat(sagaState.getPayload())
                .containsEntry("debitAttempt", 1);

        assertThat(sagaState.getUpdatedAt())
                .isEqualTo(T5);
    }
}