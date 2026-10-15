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
import org.springframework.stereotype.Service;

@Service
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
                        Instant debitProcessedAt,
                        Instant creditRequestedAt) {
                Transfer transfer = findTransferByCorrelationId(correlationId);
                SagaState sagaState = findSagaState(transfer.getId());

                transfer.completeDebit(debitProcessedAt);
                transfer.requestCredit(creditRequestedAt);
                sagaState.moveToCredit(creditRequestedAt);

                save(transfer, sagaState);

                return new SagaDecision(
                                SagaAction.REQUEST_CREDIT,
                                transfer);
        }

        @Transactional
        public SagaDecision onDebitFailed(
                        UUID correlationId,
                        String failureReason,
                        Instant failedAt) {
                Transfer transfer = findTransferByCorrelationId(correlationId);
                SagaState sagaState = findSagaState(transfer.getId());

                transfer.failDebit(failureReason, failedAt);
                sagaState.finish(failedAt);

                save(transfer, sagaState);

                return new SagaDecision(
                                SagaAction.NONE,
                                transfer);
        }

        @Transactional
        public SagaDecision onCreditCompleted(
                        UUID correlationId,
                        Instant processedAt) {
                Transfer transfer = findTransferByCorrelationId(correlationId);
                SagaState sagaState = findSagaState(transfer.getId());

                transfer.completeCredit(processedAt);
                sagaState.finish(processedAt);

                save(transfer, sagaState);

                return new SagaDecision(
                                SagaAction.NONE,
                                transfer);
        }

        @Transactional
        public SagaDecision onCreditFailed(
                        UUID correlationId,
                        String failureReason,
                        Instant creditFailedAt,
                        Instant compensationRequestedAt) {
                Transfer transfer = findTransferByCorrelationId(correlationId);
                SagaState sagaState = findSagaState(transfer.getId());

                transfer.failCredit(failureReason, creditFailedAt);
                transfer.startCompensation(compensationRequestedAt);
                sagaState.moveToCompensation(compensationRequestedAt);

                save(transfer, sagaState);

                return new SagaDecision(
                                SagaAction.REQUEST_DEBIT_COMPENSATION,
                                transfer);
        }

        @Transactional
        public SagaDecision onDebitCompensated(
                        UUID correlationId,
                        Instant compensatedAt) {
                Transfer transfer = findTransferByCorrelationId(correlationId);
                SagaState sagaState = findSagaState(transfer.getId());

                transfer.completeCompensation(compensatedAt);
                sagaState.finish(compensatedAt);

                save(transfer, sagaState);

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