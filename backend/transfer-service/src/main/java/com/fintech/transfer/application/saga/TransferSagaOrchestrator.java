package com.fintech.transfer.application.saga;

import java.time.Instant;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.fintech.transfer.domain.exception.SagaStateNotFoundException;
import com.fintech.transfer.domain.exception.TransferNotFoundException;
import com.fintech.transfer.domain.model.SagaState;
import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.out.SagaStateRepository;
import com.fintech.transfer.domain.port.out.TransferRepository;

public class TransferSagaOrchestrator {

    private final TransferRepository transferRepository;
    private final SagaStateRepository sagaStateRepository;

    public TransferSagaOrchestrator(
            TransferRepository transferRepository,
            SagaStateRepository sagaStateRepository) {
        this.transferRepository = transferRepository;
        this.sagaStateRepository = sagaStateRepository;
    }

    @Transactional
    public SagaDecision startDebit(
            UUID transferId,
            Instant now) {
        Transfer transfer = findTransfer(transferId);

        SagaState sagaState = findSagaState(transfer.getId());

        transfer.requestDebit(now);

        save(
                transfer,
                sagaState);

        return new SagaDecision(
                SagaAction.REQUEST_DEBIT,
                transfer);
    }

    @Transactional
    public SagaDecision onDebitCompleted(
            UUID correlationId,
            Instant now) {
        Transfer transfer = findTransferByCorrelationId(correlationId);

        SagaState sagaState = findSagaState(transfer.getId());

        transfer.completeDebit(now);
        sagaState.moveToCredit(now);

        save(
                transfer,
                sagaState);

        return new SagaDecision(
                SagaAction.REQUEST_CREDIT,
                transfer);
    }

    @Transactional
    public SagaDecision onDebitFailed(
            UUID correlationId,
            String failureReason,
            Instant now) {
        Transfer transfer = findTransferByCorrelationId(correlationId);

        SagaState sagaState = findSagaState(transfer.getId());

        transfer.failDebit(
                failureReason,
                now);

        sagaState.finish(now);

        save(
                transfer,
                sagaState);

        return new SagaDecision(
                SagaAction.NONE,
                transfer);
    }

    @Transactional
    public SagaDecision onCreditCompleted(
            UUID correlationId,
            Instant now) {
        Transfer transfer = findTransferByCorrelationId(correlationId);

        SagaState sagaState = findSagaState(transfer.getId());

        transfer.completeCredit(now);
        sagaState.finish(now);

        save(
                transfer,
                sagaState);

        return new SagaDecision(
                SagaAction.NONE,
                transfer);
    }

    @Transactional
    public SagaDecision onCreditFailed(
            UUID correlationId,
            String failureReason,
            Instant now) {
        Transfer transfer = findTransferByCorrelationId(correlationId);

        SagaState sagaState = findSagaState(transfer.getId());

        transfer.failCredit(
                failureReason,
                now);

        sagaState.moveToCompensation(now);

        save(
                transfer,
                sagaState);

        return new SagaDecision(
                SagaAction.REQUEST_DEBIT_COMPENSATION,
                transfer);
    }

    @Transactional
    public SagaDecision onDebitCompensated(
            UUID correlationId,
            Instant now) {
        Transfer transfer = findTransferByCorrelationId(correlationId);

        SagaState sagaState = findSagaState(transfer.getId());

        transfer.completeCompensation(now);
        sagaState.finish(now);

        save(
                transfer,
                sagaState);

        return new SagaDecision(
                SagaAction.NONE,
                transfer);
    }

    private Transfer findTransfer(UUID transferId) {
        return transferRepository
                .findById(transferId)
                .orElseThrow(
                        () -> new TransferNotFoundException(transferId));
    }

    private Transfer findTransferByCorrelationId(
            UUID correlationId) {
        return transferRepository
                .findByCorrelationId(correlationId)
                .orElseThrow(
                        () -> new TransferNotFoundException(
                                correlationId));
    }

    private SagaState findSagaState(UUID transferId) {
        return sagaStateRepository
                .findByTransferId(transferId)
                .orElseThrow(
                        () -> new SagaStateNotFoundException(
                                transferId));
    }

    private void save(
            Transfer transfer,
            SagaState sagaState) {
        transferRepository.save(transfer);
        sagaStateRepository.save(sagaState);
    }
}