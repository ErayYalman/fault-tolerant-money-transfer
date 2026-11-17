package com.fintech.transfer.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.transfer.domain.model.TransferPage;
import com.fintech.transfer.domain.port.in.ListTransfersUseCase;
import com.fintech.transfer.domain.port.out.TransferRepository;

@Service
public class TransferHistoryQueryService implements ListTransfersUseCase {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransferRepository transferRepository;

    public TransferHistoryQueryService(
            TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public TransferPage listByOwner(
            String ownerSubject,
            int page,
            int size) {

        if (ownerSubject == null || ownerSubject.isBlank()) {
            throw new IllegalArgumentException(
                    "ownerSubject must not be blank");
        }

        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must not be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        return transferRepository.findAllByOwnerSubject(
                ownerSubject,
                page,
                size
        );
    }
}
