package com.fintech.transfer.application.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fintech.transfer.domain.model.Transfer;
import com.fintech.transfer.domain.port.out.TransferRepository;

@Service
public class TransferLifecycleQueryService {

    private static final int MAX_PAGE_SIZE = 1000;

    private final TransferRepository transferRepository;

    public TransferLifecycleQueryService(
            TransferRepository transferRepository) {
        this.transferRepository = transferRepository;
    }

    @Transactional(readOnly = true)
    public List<Transfer> findByLifecycleRange(
            Instant fromInclusive,
            Instant toExclusive,
            int page,
            int size) {

        if (fromInclusive == null || toExclusive == null) {
            throw new IllegalArgumentException(
                    "from and to must not be null");
        }

        if (!fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException(
                    "from must be earlier than to");
        }

        if (page < 0) {
            throw new IllegalArgumentException(
                    "page must not be negative");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "size must be between 1 and " + MAX_PAGE_SIZE);
        }

        return transferRepository.findByLifecycleRange(
                fromInclusive,
                toExclusive,
                page,
                size);
    }
}