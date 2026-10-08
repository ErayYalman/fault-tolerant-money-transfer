package com.fintech.transfer.unit.domain.model;

import com.fintech.transfer.domain.exception.InvalidTransferStateTransitionException;
import com.fintech.transfer.domain.model.Money;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.model.TransferStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransferTest {

    private static final Instant T1 =
            Instant.parse("2026-10-08T10:00:00Z");

    private static final Instant T2 =
            Instant.parse("2026-10-08T10:00:01Z");

    private static final Instant T3 =
            Instant.parse("2026-10-08T10:00:02Z");

    private static final Instant T4 =
            Instant.parse("2026-10-08T10:00:03Z");

    private static final Instant T5 =
            Instant.parse("2026-10-08T10:00:04Z");

    private static final Instant T6 =
            Instant.parse("2026-10-08T10:00:05Z");

    private static final Instant T7 =
            Instant.parse("2026-10-08T10:00:06Z");

    @Test
    void shouldCreateTransferInPendingState() {
        Transfer transfer = createPendingTransfer();

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.PENDING);

        assertThat(transfer.getStartedAt())
                .isEqualTo(T1);

        assertThat(transfer.getCreatedAt())
                .isEqualTo(T1);

        assertThat(transfer.getUpdatedAt())
                .isEqualTo(T1);

        assertThat(transfer.getFailureReason())
                .isNull();
    }

    @Test
    void shouldMoveFromPendingToDebitRequested() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.DEBIT_REQUESTED);

        assertThat(transfer.getDebitRequestedAt())
                .isEqualTo(T2);

        assertThat(transfer.getUpdatedAt())
                .isEqualTo(T2);
    }

    @Test
    void shouldMoveFromDebitRequestedToDebitCompleted() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.DEBIT_COMPLETED);

        assertThat(transfer.getDebitRequestedAt())
                .isEqualTo(T2);

        assertThat(transfer.getDebitCompletedAt())
                .isEqualTo(T3);
    }

    @Test
    void shouldMoveFromDebitRequestedToDebitFailed() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);
        transfer.failDebit(
                "INSUFFICIENT_FUNDS",
                T3
        );

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.DEBIT_FAILED);

        assertThat(transfer.getFailureReason())
                .isEqualTo("INSUFFICIENT_FUNDS");

        assertThat(transfer.getCompletedAt())
                .isNull();
    }

    @Test
    void shouldMoveFromDebitCompletedToCreditRequested() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);
        transfer.requestCredit(T4);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.CREDIT_REQUESTED);

        assertThat(transfer.getCreditRequestedAt())
                .isEqualTo(T4);
    }

    @Test
    void shouldCompleteSuccessfulTransfer() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);
        transfer.requestCredit(T4);
        transfer.completeCredit(T5);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.COMPLETED);

        assertThat(transfer.getCreditCompletedAt())
                .isEqualTo(T5);

        assertThat(transfer.getCompletedAt())
                .isEqualTo(T5);

        assertThat(transfer.getFailureReason())
                .isNull();

        assertThat(transfer.getUpdatedAt())
                .isEqualTo(T5);
    }

    @Test
    void shouldMoveFromCreditRequestedToCreditFailed() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);
        transfer.requestCredit(T4);
        transfer.failCredit(
                "CREDIT_SERVICE_FAILURE",
                T5
        );

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.CREDIT_FAILED);

        assertThat(transfer.getFailureReason())
                .isEqualTo("CREDIT_SERVICE_FAILURE");

        assertThat(transfer.getCompletedAt())
                .isNull();
    }

    @Test
    void shouldMoveFromCreditFailedToCompensating() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);
        transfer.requestCredit(T4);
        transfer.failCredit(
                "CREDIT_SERVICE_FAILURE",
                T5
        );

        transfer.startCompensation(T6);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.COMPENSATING);

        assertThat(transfer.getFailureReason())
                .isEqualTo("CREDIT_SERVICE_FAILURE");
    }

    @Test
    void shouldCompleteCompensation() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);
        transfer.requestCredit(T4);
        transfer.failCredit(
                "CREDIT_SERVICE_FAILURE",
                T5
        );
        transfer.startCompensation(T6);
        transfer.completeCompensation(T7);

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.COMPENSATED);

        assertThat(transfer.getCompletedAt())
                .isEqualTo(T7);

        assertThat(transfer.getFailureReason())
                .isNull();

        assertThat(transfer.getUpdatedAt())
                .isEqualTo(T7);
    }

    @Test
    void shouldRejectInvalidTransitionFromPendingToCreditRequested() {
        Transfer transfer = createPendingTransfer();

        assertThatThrownBy(() ->
                transfer.requestCredit(T2)
        )
                .isInstanceOf(
                        InvalidTransferStateTransitionException.class
                )
                .hasMessage(
                        "Invalid transfer state transition: "
                                + "PENDING -> CREDIT_REQUESTED"
                );
    }

    @Test
    void shouldRejectInvalidTransitionFromDebitCompletedToCompleted() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);

        assertThatThrownBy(() ->
                transfer.completeCredit(T4)
        )
                .isInstanceOf(
                        InvalidTransferStateTransitionException.class
                );
    }

    @Test
    void shouldRejectCompensationWhenCreditDidNotFail() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);
        transfer.requestCredit(T4);

        assertThatThrownBy(() ->
                transfer.startCompensation(T5)
        )
                .isInstanceOf(
                        InvalidTransferStateTransitionException.class
                );
    }

    @Test
    void shouldRejectTransitionFromTerminalState() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);
        transfer.failDebit(
                "INSUFFICIENT_FUNDS",
                T3
        );

        assertThatThrownBy(() ->
                transfer.requestDebit(T4)
        )
                .isInstanceOf(
                        InvalidTransferStateTransitionException.class
                );
    }

    @Test
    void shouldNotApplyDuplicateDebitCompletedEvent() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);
        transfer.completeDebit(T3);

        assertThatThrownBy(() ->
                transfer.completeDebit(T4)
        )
                .isInstanceOf(
                        InvalidTransferStateTransitionException.class
                );

        assertThat(transfer.getStatus())
                .isEqualTo(TransferStatus.DEBIT_COMPLETED);

        assertThat(transfer.getDebitCompletedAt())
                .isEqualTo(T3);

        assertThat(transfer.getUpdatedAt())
                .isEqualTo(T3);
    }

    @Test
    void shouldRejectBlankFailureReason() {
        Transfer transfer = createPendingTransfer();

        transfer.requestDebit(T2);

        assertThatThrownBy(() ->
                transfer.failDebit("", T3)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "failureReason must not be blank"
                );
    }

    private Transfer createPendingTransfer() {
        return Transfer.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "state-test-" + UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new Money(
                        new BigDecimal("1000.00"),
                        "TRY"
                ),
                T1
        );
    }
}